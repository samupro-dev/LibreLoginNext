/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.config.key;

import java.util.function.BiFunction;
import xyz.kyngs.librelogin.common.config.ConfigurateHelper;

/**
 * Represents a strongly-typed configuration or message key entry with its path, default value,
 * associated comment, and deserialization getter.
 *
 * @param <T> the type of value represented by this configuration key
 */
public class ConfigurationKey<T> {

    private final String key;
    private final BiFunction<ConfigurateHelper, String, T> getter;
    private T defaultValue;
    private String comment;

    /**
     * Constructs a new configuration key.
     *
     * @param key the configuration path key
     * @param defaultValue the fallback default value
     * @param comment the HOCON documentation comment
     * @param getter the function extracting the value from {@link ConfigurateHelper}
     */
    public ConfigurationKey(
            String key,
            T defaultValue,
            String comment,
            BiFunction<ConfigurateHelper, String, T> getter) {
        this.key = key;
        this.getter = getter;
        this.defaultValue = defaultValue;
        this.comment = comment;
    }

    /**
     * Creates a section-only comment configuration node without a getter or default value.
     *
     * @param key the section key
     * @param comment the section header comment
     * @return a comment-only configuration key
     */
    public static ConfigurationKey<?> getComment(String key, String comment) {
        return new ConfigurationKey<>(
                key,
                null,
                comment,
                (x, y) -> {
                    throw new UnsupportedOperationException();
                });
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String key() {
        return key;
    }

    public BiFunction<ConfigurateHelper, String, T> getter() {
        return getter;
    }

    public T defaultValue() {
        return defaultValue;
    }

    public String comment() {
        return comment;
    }

    public void setDefault(T defaultValue) {
        this.defaultValue = defaultValue;
    }

    /**
     * Extracts the strongly-typed value from the configuration helper, falling back to default if
     * null.
     *
     * @param configurateHelper the helper wrapper
     * @return the resolved value or default
     */
    public T compute(ConfigurateHelper configurateHelper) {
        var value = getter.apply(configurateHelper, key());

        return value == null ? defaultValue : value;
    }
}
