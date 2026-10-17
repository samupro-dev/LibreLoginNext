/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.config;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collection;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.ConfigurationOptions;
import org.spongepowered.configurate.hocon.HoconConfigurationLoader;
import xyz.kyngs.librelogin.api.BiHolder;
import xyz.kyngs.librelogin.api.Logger;
import xyz.kyngs.librelogin.api.configuration.CorruptedConfigurationException;
import xyz.kyngs.librelogin.common.config.key.ConfigurationKey;
import xyz.kyngs.librelogin.common.config.migrate.ConfigurationMigrator;
import xyz.kyngs.librelogin.common.util.GeneralUtil;

/**
 * Manages loading, saving, migration, and default key merging for HOCON configuration files via
 * Configurate.
 */
public class ConfigurateConfiguration {

    private final ConfigurateHelper helper;
    private final boolean newlyCreated;
    private final HoconConfigurationLoader loader;
    private final boolean usesSubstitutions;
    private final Logger logger;
    private final String name;

    /**
     * Loads or initializes a HOCON configuration file, applying default keys, headers, and
     * migrators.
     *
     * @param dataFolder the plugin data folder
     * @param name the configuration file name
     * @param defaultKeys collection of key-defining classes paired with path prefixes
     * @param comment the root header comment
     * @param logger the plugin logger
     * @param migrators sequential configuration migrators
     * @throws IOException if file creation or saving fails
     * @throws CorruptedConfigurationException if syntax or serialization error occurs
     */
    public ConfigurateConfiguration(
            File dataFolder,
            String name,
            Collection<BiHolder<Class<?>, String>> defaultKeys,
            String comment,
            Logger logger,
            ConfigurationMigrator... migrators)
            throws IOException, CorruptedConfigurationException {
        var revision = migrators.length;
        var file = new File(dataFolder, name);
        this.logger = logger;
        this.name = name;
        // Configurate resolves ${...} / ${?ENV} while loading; saving would replace them with the
        // resolved values and store secrets in plain text, so such files are never rewritten.
        usesSubstitutions =
                file.exists()
                        && Files.readString(file.toPath(), StandardCharsets.UTF_8).contains("${");

        if (!file.exists()) {
            newlyCreated = true;
            if (!file.createNewFile()) {
                throw new IOException("Could not create configuration file!");
            }
        } else {
            newlyCreated = false;
        }

        var refHelper = new ConfigurateHelper(CommentedConfigurationNode.root().comment(comment));

        var extractedKeys =
                defaultKeys.stream()
                        .map(
                                data ->
                                        new BiHolder<>(
                                                GeneralUtil.extractKeys(data.key()), data.value()))
                        .toList();

        for (var key : extractedKeys) {
            for (ConfigurationKey<?> configurationKey : key.key()) {
                refHelper.setDefault(configurationKey, key.value());
            }
        }

        var ref = refHelper.configuration();

        var builder =
                HoconConfigurationLoader.builder()
                        .defaultOptions(ConfigurationOptions.defaults().header(ref.comment()))
                        .file(file)
                        .emitComments(true)
                        .prettyPrinting(true);

        loader = builder.build();

        try {
            helper = new ConfigurateHelper(loader.load().mergeFrom(ref));
        } catch (ConfigurateException e) {
            throw new CorruptedConfigurationException(e);
        }

        var presentRevision = helper.getInt("revision");

        if (presentRevision == null) {
            presentRevision = newlyCreated ? revision : 0;
        }

        if (presentRevision < revision) {
            for (int i = presentRevision; i < revision; i++) {
                migrators[i].migrate(helper, logger);
            }
        }

        helper.configuration().mergeFrom(ref);

        helper.configuration()
                .node("revision")
                .set(revision)
                .comment("The config revision number. !!DO NOT TOUCH THIS!!");

        for (var key : extractedKeys) {
            for (ConfigurationKey<?> configurationKey : key.key()) {
                helper.setComment(configurationKey, key.value());
            }
        }

        save();
    }

    /**
     * Obtains the helper wrapping the underlying configuration node.
     *
     * @return the configuration helper
     */
    public ConfigurateHelper getHelper() {
        return helper;
    }

    /**
     * Checks if this configuration file was newly created during initialization.
     *
     * @return true if the file was created anew
     */
    public boolean isNewlyCreated() {
        return newlyCreated;
    }

    /**
     * Saves current node state to disk.
     *
     * @throws IOException if writing to disk fails
     */
    /**
     * Returns whether {@link #save()} writes to disk (false when the file uses substitutions).
     *
     * @return {@code true} if changes can be saved
     */
    public boolean isWritable() {
        return !usesSubstitutions;
    }

    public void save() throws IOException {
        if (usesSubstitutions) {
            logger.info(
                    name
                            + " uses ${...} substitutions, so it is not rewritten (new options and"
                            + " comments are not added; their defaults apply).");
            return;
        }
        loader.save(helper.configuration());
    }
}
