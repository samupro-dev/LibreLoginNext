/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.configuration;

import java.io.IOException;
import xyz.kyngs.librelogin.api.LibreLoginPlugin;
import xyz.kyngs.librelogin.lib.adventure.text.TextComponent;

/**
 * Message provider handling internationalization, placeholder resolution, and localized text
 * components.
 */
public interface Messages {

    /**
     * Retrieves a message component by key with placeholder replacements applied.
     *
     * @param key the message key identifier
     * @param replacements alternating placeholder key-value pairs (e.g. "%player%", "kyngs")
     * @return the parsed text component, or null if the key is not defined
     */
    TextComponent getMessage(String key, String... replacements);

    /**
     * Checks whether the message associated with the specified key is empty or blank.
     *
     * @param key the message key identifier
     * @return true if the message is empty or undefined, false otherwise
     */
    boolean isEmpty(String key);

    /**
     * Reloads message templates and translations from configuration files.
     *
     * @param plugin the plugin instance
     * @throws IOException if an I/O error occurs while reading the configuration file
     * @throws CorruptedConfigurationException if the configuration file has invalid formatting or
     *     structure
     */
    void reload(LibreLoginPlugin<?, ?> plugin) throws IOException, CorruptedConfigurationException;
}
