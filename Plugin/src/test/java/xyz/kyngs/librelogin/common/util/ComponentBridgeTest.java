/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import xyz.kyngs.librelogin.lib.adventure.text.Component;
import xyz.kyngs.librelogin.lib.adventure.text.minimessage.MiniMessage;

class ComponentBridgeTest {

    private final Component message =
            MiniMessage.miniMessage()
                    .deserialize(
                            "<#ff0000>Hello <click:open_url:'https://example.com'>link</click>");

    @Test
    void jsonRoundTripKeepsContent() {
        var json = ComponentBridge.toJson(message);
        assertEquals(message.compact(), ComponentBridge.fromJson(json).compact());
    }

    @Test
    void jsonCarriesLegacyAndModernClickKeys() {
        // Older native Adventure (Paper 1.16.5) reads clickEvent, newer versions click_event.
        var json = ComponentBridge.toJson(message);
        assertTrue(json.contains("clickEvent"), json);
        assertTrue(json.contains("click_event"), json);
    }

    @Test
    void legacyTextDownsamplesOrKeepsHex() {
        assertEquals("§x§f§f§0§0§0§0Hello link", ComponentBridge.toLegacy(message, true));
        // Pre-1.16 clients: RGB is downsampled to the nearest named color, never sent as §x.
        var downsampled = ComponentBridge.toLegacy(message, false);
        assertTrue(!downsampled.contains("§x") && downsampled.endsWith("Hello link"), downsampled);
    }

    @Test
    void nullBecomesEmpty() {
        assertEquals("", ComponentBridge.toLegacy(null, true));
    }
}
