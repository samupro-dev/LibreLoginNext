/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.config;

import com.google.common.base.Splitter;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import xyz.kyngs.librelogin.api.configuration.CorruptedConfigurationException;
import xyz.kyngs.librelogin.api.util.ThrowableFunction;
import xyz.kyngs.librelogin.common.config.key.ConfigurationKey;

/**
 * Helper record wrapping a Configurate {@link CommentedConfigurationNode} with convenient typed
 * accessors, path resolvers, and default value injectors.
 *
 * @param configuration the root commented configuration node
 */
public record ConfigurateHelper(CommentedConfigurationNode configuration) {

    /**
     * Reads a String value from the specified path.
     *
     * @param path the dot-separated path
     * @return the string value, or null
     */
    public String getString(String path) {
        return get(String.class, path);
    }

    /**
     * Reads an Integer value from the specified path.
     *
     * @param path the dot-separated path
     * @return the integer value, or null
     */
    public Integer getInt(String path) {
        return get(Integer.class, path);
    }

    /**
     * Reads a Boolean value from the specified path.
     *
     * @param path the dot-separated path
     * @return the boolean value, or null
     */
    public Boolean getBoolean(String path) {
        return get(Boolean.class, path);
    }

    /**
     * Reads a Long value from the specified path.
     *
     * @param path the dot-separated path
     * @return the long value, or null
     */
    public Long getLong(String path) {
        return get(Long.class, path);
    }

    /**
     * Reads a typed object from the specified path.
     *
     * @param <T> the target type
     * @param clazz the target class
     * @param path the dot-separated path
     * @return the typed object, or null
     */
    public <T> T get(Class<T> clazz, String path) {
        return configurationFunction(
                path,
                node -> {
                    if (node.isList()) {
                        throw new CorruptedConfigurationException("Node is a list!");
                    }
                    return node.get(clazz);
                });
    }

    /**
     * Reads a list of Strings from the specified path.
     *
     * @param path the dot-separated path
     * @return the string list, or null
     */
    public List<String> getStringList(String path) {
        return getList(String.class, path);
    }

    /**
     * Reads a list of typed objects from the specified path.
     *
     * @param <T> the element type
     * @param clazz the element class
     * @param path the dot-separated path
     * @return the typed list, or null
     */
    public <T> List<T> getList(Class<T> clazz, String path) {
        return configurationFunction(
                path,
                node -> {
                    if (!node.isList()) {
                        throw new CorruptedConfigurationException("Node is not a list!");
                    }
                    return node.getList(clazz);
                });
    }

    /**
     * Reads a multimap representing server mappings (host -> list of servers).
     *
     * @param path the dot-separated path
     * @return the resolved multimap
     */
    public Multimap<String, String> getServerMap(String path) {
        return configurationFunction(
                path,
                node -> {
                    if (!node.isMap()) {
                        throw new CorruptedConfigurationException("Node is not a map!");
                    }

                    var map = HashMultimap.<String, String>create();

                    for (Map.Entry<Object, CommentedConfigurationNode> entry :
                            node.childrenMap().entrySet()) {
                        if (!entry.getValue().isList()) {
                            throw new CorruptedConfigurationException("Node is not a list!");
                        }

                        var list = entry.getValue().getList(String.class);

                        if (list == null) {
                            throw new CorruptedConfigurationException("List is null!");
                        }

                        for (String s : list) {
                            map.put(entry.getKey().toString().replace('§', '.'), s);
                        }
                    }

                    return map;
                });
    }

    /**
     * Sets a value at the specified dot-separated path.
     *
     * @param path the dot-separated path
     * @param value the value to set
     */
    public void set(String path, Object value) {
        try {
            var node = resolve(path);
            if (value instanceof Multimap<?, ?> multimap) {
                for (Map.Entry<?, ? extends Collection<?>> entry : multimap.asMap().entrySet()) {
                    node.node(entry.getKey().toString()).set(entry.getValue());
                }
            } else {
                node.set(value);
            }
        } catch (SerializationException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Resolves the {@link CommentedConfigurationNode} for a dot-separated key path.
     *
     * @param key the dot-separated path
     * @return the resolved configuration node
     */
    public CommentedConfigurationNode resolve(String key) {
        return configuration.node(Splitter.on('.').splitToList(key).toArray());
    }

    /**
     * Executes a function against a resolved node safely, catching exceptions and returning null on
     * failure.
     *
     * @param <T> the return type
     * @param path the node path
     * @param function the function to execute
     * @return the result, or null if node is virtual or error occurs
     */
    public <T> T configurationFunction(
            String path, ThrowableFunction<CommentedConfigurationNode, T, Exception> function) {
        try {
            var node = resolve(path);
            if (node == null || node.virtual()) {
                return null;
            }
            return function.apply(node);
        } catch (CorruptedConfigurationException e) {
            throw new RuntimeException(
                    "Corrupted configuration at '" + path + "': " + e.getMessage(), e);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Sets the default value of a configuration key if non-null.
     *
     * @param key the configuration key
     * @param prefix the path prefix
     */
    public void setDefault(ConfigurationKey<?> key, String prefix) {
        var defaultValue = key.defaultValue();

        if (defaultValue != null) {
            set(prefix + key.key(), defaultValue);
        }
    }

    /**
     * Applies the comment of a configuration key to the node at the prefixed path.
     *
     * @param key the configuration key
     * @param prefix the path prefix
     */
    public void setComment(ConfigurationKey<?> key, String prefix) {
        resolve(prefix + key.key()).comment(key.comment());
    }

    /**
     * Computes the strongly-typed value for a {@link ConfigurationKey}.
     *
     * @param <T> the value type
     * @param key the configuration key
     * @return the extracted value
     */
    public <T> T get(ConfigurationKey<T> key) {
        return key.compute(this);
    }
}
