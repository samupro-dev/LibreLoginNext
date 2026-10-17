/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.util;

import xyz.kyngs.librelogin.lib.adventure.text.Component;
import xyz.kyngs.librelogin.lib.adventure.text.serializer.gson.GsonComponentSerializer;
import xyz.kyngs.librelogin.lib.adventure.text.serializer.json.JSONOptions;
import xyz.kyngs.librelogin.lib.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Converts LibreLogin's relocated Adventure components into representations every platform
 * understands. Components are always built by the bundled (relocated) MiniMessage; this class is
 * the only place where they leave that copy of Adventure.
 */
public final class ComponentBridge {

    /**
     * JSON in the most widely understood shape: both legacy and modern click/hover keys, so any
     * native Adventure version (4.7 on Paper 1.16.5 up to 5.x on Velocity 4 / Paper 26.2+) and
     * PacketEvents can read it without losing information.
     */
    private static final GsonComponentSerializer JSON =
            GsonComponentSerializer.builder().options(JSONOptions.compatibility()).build();

    private static final LegacyComponentSerializer LEGACY_HEX =
            LegacyComponentSerializer.builder()
                    .character(LegacyComponentSerializer.SECTION_CHAR)
                    .hexColors()
                    .useUnusualXRepeatedCharacterHexFormat()
                    .build();

    private static final LegacyComponentSerializer LEGACY =
            LegacyComponentSerializer.legacySection();

    private ComponentBridge() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Serializes a component to compatibility JSON.
     *
     * @param component the component
     * @return the JSON text
     */
    public static String toJson(Component component) {
        return JSON.serialize(component == null ? Component.empty() : component);
    }

    /**
     * Parses JSON produced by a platform serializer back into a relocated component.
     *
     * @param json the JSON text
     * @return the component
     */
    public static Component fromJson(String json) {
        return JSON.deserialize(json);
    }

    /**
     * Serializes a component to a section-sign legacy string.
     *
     * @param component the component
     * @param hexColors whether the platform understands {@code §x§r§r§g§g§b§b} RGB colors (1.16+)
     * @return the legacy text
     */
    public static String toLegacy(Component component, boolean hexColors) {
        var value = component == null ? Component.empty() : component;
        return (hexColors ? LEGACY_HEX : LEGACY).serialize(value);
    }
}
