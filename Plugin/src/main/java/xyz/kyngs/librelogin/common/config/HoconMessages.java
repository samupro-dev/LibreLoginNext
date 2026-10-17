/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.config;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.spongepowered.configurate.CommentedConfigurationNode;
import xyz.kyngs.librelogin.api.BiHolder;
import xyz.kyngs.librelogin.api.LibreLoginPlugin;
import xyz.kyngs.librelogin.api.Logger;
import xyz.kyngs.librelogin.api.configuration.CorruptedConfigurationException;
import xyz.kyngs.librelogin.api.configuration.Messages;
import xyz.kyngs.librelogin.common.config.migrate.ConfigurationMigrator;
import xyz.kyngs.librelogin.common.util.GeneralUtil;
import xyz.kyngs.librelogin.lib.adventure.text.Component;
import xyz.kyngs.librelogin.lib.adventure.text.TextComponent;
import xyz.kyngs.librelogin.lib.adventure.text.minimessage.MiniMessage;

/**
 * Implementation of {@link Messages} that loads, migrates, and parses localized messages from
 * messages.conf. Supports legacy color codes '&' and Kyori MiniMessage formatting tags.
 */
public class HoconMessages implements Messages {

    private static final MiniMessage SERIALIZER = MiniMessage.miniMessage();
    // Replaced as a whole on reload, so readers never see a partially filled map.
    private volatile Map<String, TextComponent> messages;
    private final Logger logger;
    private volatile ConfigurateConfiguration rawMessages;

    /**
     * Constructs a new HOCON messages provider.
     *
     * @param logger the plugin logger
     */
    public HoconMessages(Logger logger) {
        this.logger = logger;
        messages = new ConcurrentHashMap<>();
    }

    /**
     * Obtains the live map of parsed text components keyed by message identifier.
     *
     * @return the messages map
     */
    public Map<String, TextComponent> getMessages() {
        return messages;
    }

    @Override
    public TextComponent getMessage(String key, String... replacements) {
        var message = messages.get(key);

        if (replacements.length == 0) {
            return message;
        }

        var replaceMap = new HashMap<String, String>();
        String toReplace = null;

        for (int i = 0; i < replacements.length; i++) {
            if (i % 2 != 0) {
                replaceMap.put(toReplace, replacements[i]);
            } else {
                toReplace = replacements[i];
            }
        }

        return GeneralUtil.formatComponent(message, replaceMap);
    }

    @Override
    public void reload(LibreLoginPlugin<?, ?> plugin)
            throws IOException, CorruptedConfigurationException {
        var adept =
                new ConfigurateConfiguration(
                        plugin.getDataFolder(),
                        "messages.conf",
                        Set.of(new BiHolder<>(MessageKeys.class, "")),
"""
  !!THIS FILE IS WRITTEN IN THE HOCON FORMAT!!
  The hocon format is very similar to JSON, but it has some extra features.
  You can find more information about the format on the sponge wiki:
  https://docs.spongepowered.org/stable/en/server/getting-started/configuration/hocon.html
  ----------------------------------------------------------------------------------------
  LibreLogin Next Messages
  ----------------------------------------------------------------------------------------
  This file contains all of the messages used by the plugin, you are welcome to fit it to your needs.
  The messages can be written both in the legacy format and in the MiniMessage format. For example, the following message is completely valid: <bold>&aReloaded!</bold>
  Note: "&" followed by 0-9, a-f, k-o or r is always read as a legacy color/format code, so plain text such as "R&D" turns into "R" + light purple. Legacy codes are translated into MiniMessage tags, which nest instead of resetting the previous formatting.
  The email.* entries are HTML and are used as written (no MiniMessage).
  You can find more information about LibreLogin Next on the github page:
  https://github.com/samupro-dev/LibreLoginNext
""",
                        logger,
                        new ConfigurationMigrator[0]);

        var parsed = new ConcurrentHashMap<String, TextComponent>();
        extractKeys("", adept.getHelper().configuration(), parsed);
        messages = parsed;
        rawMessages = adept;
    }

    private void extractKeys(
            String prefix, CommentedConfigurationNode node, Map<String, TextComponent> target) {
        node.childrenMap()
                .forEach(
                        (key, value) -> {
                            if (!(key instanceof String str)) {
                                return;
                            }

                            if (value.childrenMap().isEmpty()) {
                                var string = value.getString();

                                if (string == null) {
                                    return;
                                }

                                // HTML email templates are read raw (getRawMessage); parsing them
                                // as
                                // MiniMessage only wastes work and mangles entities such as &lt;.
                                if ((prefix + str).startsWith("email.")) {
                                    return;
                                }

                                target.put(
                                        prefix + str,
                                        (TextComponent)
                                                Component.empty()
                                                        .append(
                                                                SERIALIZER.deserialize(
                                                                        GeneralUtil
                                                                                .translateLegacyToMiniMessage(
                                                                                        string))));
                            } else {
                                extractKeys(prefix + str + ".", value, target);
                            }
                        });
    }

    /**
     * Retrieves the unformatted raw string representation of a message from the configuration.
     *
     * @param key the message key
     * @return the raw string or null
     */
    public String getRawMessage(String key) {
        return rawMessages == null ? null : rawMessages.getHelper().getString(key);
    }

    @Override
    public boolean isEmpty(String key) {
        var raw = getRawMessage(key);
        return raw != null && raw.equals(" ");
    }
}
