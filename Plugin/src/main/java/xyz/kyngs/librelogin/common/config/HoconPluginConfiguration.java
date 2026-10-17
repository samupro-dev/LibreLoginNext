/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.config;

import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.NEW_UUID_CREATOR;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import xyz.kyngs.librelogin.api.BiHolder;
import xyz.kyngs.librelogin.api.LibreLoginPlugin;
import xyz.kyngs.librelogin.api.Logger;
import xyz.kyngs.librelogin.api.configuration.CorruptedConfigurationException;
import xyz.kyngs.librelogin.common.config.key.ConfigurationKey;
import xyz.kyngs.librelogin.common.config.migrate.ConfigurationMigrator;

/**
 * High-level manager for loading, validating, and querying LibreLogin's main configuration file
 * (config.conf).
 */
public class HoconPluginConfiguration {

    private final Logger logger;
    private final Collection<BiHolder<Class<?>, String>> defaultKeys;
    // Replaced as a pair on reload; values are computed once per key instead of walking the
    // Configurate tree on every get().
    private volatile ConfigurateHelper helper;
    private volatile Map<ConfigurationKey<?>, Optional<Object>> cache = new ConcurrentHashMap<>();

    /**
     * Constructs a new HOCON plugin configuration manager.
     *
     * @param logger the plugin logger
     * @param defaultKeys the default configuration key definitions
     */
    public HoconPluginConfiguration(
            Logger logger, Collection<BiHolder<Class<?>, String>> defaultKeys) {
        this.logger = logger;
        this.defaultKeys = new ArrayList<>(defaultKeys);
        this.defaultKeys.add(new BiHolder<>(ConfigurationKeys.class, ""));
    }

    /**
     * Obtains the underlying configuration helper.
     *
     * @return the configuration helper
     */
    public ConfigurateHelper getHelper() {
        return helper;
    }

    /**
     * Reloads or initializes the configuration from config.conf.
     *
     * @param plugin the plugin instance
     * @return true if a new configuration file was created
     * @throws IOException if disk I/O fails
     * @throws CorruptedConfigurationException if configuration values or format are invalid
     */
    public boolean reload(LibreLoginPlugin<?, ?> plugin)
            throws IOException, CorruptedConfigurationException {
        var adept =
                new ConfigurateConfiguration(
                        plugin.getDataFolder(),
                        "config.conf",
                        defaultKeys,
"""
  !!THIS FILE IS WRITTEN IN THE HOCON FORMAT!!
  The hocon format is very similar to JSON, but it has some extra features.
  You can find more information about the format on the sponge wiki:
  https://docs.spongepowered.org/stable/en/server/getting-started/configuration/hocon.html
  ----------------------------------------------------------------------------------------
  LibreLogin Next Configuration
  ----------------------------------------------------------------------------------------
  This is the configuration file for LibreLogin Next.
  You can find more information about LibreLogin Next on the github page:
  https://github.com/samupro-dev/LibreLoginNext
""",
                        logger,
                        new ConfigurationMigrator[0]);

        var helperAdept = adept.getHelper();

        if (!adept.isNewlyCreated()) {
            var problems = ConfigurationValidator.validate(helperAdept, plugin::getCryptoProvider);
            if (!problems.isEmpty()) {
                // Nothing is swapped in: on reload the previous configuration stays active.
                throw new CorruptedConfigurationException(
                        "Invalid config.conf values:\n - " + String.join("\n - ", problems));
            }
        }

        var salt = helperAdept.get(ConfigurationKeys.IP_HASH_SALT);
        if (salt == null
                || salt.isBlank()
                || salt.equals(ConfigurationKeys.IP_HASH_SALT.defaultValue())) {
            if (adept.isWritable()) {
                // With the shipped salt, hashed IPv4 addresses can be reversed by brute force.
                var bytes = new byte[32];
                new SecureRandom().nextBytes(bytes);
                helperAdept.set(
                        ConfigurationKeys.IP_HASH_SALT.key(),
                        Base64.getEncoder().withoutPadding().encodeToString(bytes));
                adept.save();
                logger.warn(
                        "privacy.ip-hash-salt was the public default; a random secret was generated"
                            + " and saved to config.conf. Remembered IP sessions are reset once.");
            } else {
                logger.warn(
                        "privacy.ip-hash-salt is the public default and config.conf is not"
                            + " rewritten (it uses ${...}); set a random secret yourself, otherwise"
                            + " hashed IP addresses can be reversed.");
            }
        }

        cache = new ConcurrentHashMap<>();
        helper = helperAdept;

        return adept.isNewlyCreated();
    }

    /**
     * Resolves the configured {@link NewUUIDCreator} strategy.
     *
     * @return the UUID creation strategy
     */
    public NewUUIDCreator getNewUUIDCreator() {
        var name = get(NEW_UUID_CREATOR);
        if (name == null || name.isBlank()) {
            // Documented default; validation rejects unknown values at load time.
            return NewUUIDCreator.CRACKED;
        }

        try {
            return NewUUIDCreator.valueOf(name.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return NewUUIDCreator.CRACKED;
        }
    }

    /**
     * Reads a strongly-typed configuration key value.
     *
     * @param <T> the value type
     * @param key the configuration key
     * @return the resolved configuration value
     */
    @SuppressWarnings("unchecked")
    public <T> T get(ConfigurationKey<T> key) {
        var current = helper;
        if (current == null) {
            return key.defaultValue(); // before the first load (e.g. early debug logging)
        }
        var values = cache;
        var cached = values.get(key);
        if (cached == null) {
            cached = Optional.ofNullable(current.get(key));
            values.put(key, cached);
        }
        return (T) cached.orElse(null);
    }
}
