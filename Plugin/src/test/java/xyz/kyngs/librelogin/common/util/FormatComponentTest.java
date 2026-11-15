/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;
import org.junit.jupiter.api.Test;
import xyz.kyngs.librelogin.lib.adventure.text.Component;
import xyz.kyngs.librelogin.lib.adventure.text.TextComponent;
import xyz.kyngs.librelogin.lib.adventure.text.event.ClickEvent;
import xyz.kyngs.librelogin.lib.adventure.text.minimessage.MiniMessage;
import xyz.kyngs.librelogin.lib.adventure.text.serializer.plain.PlainTextComponentSerializer;

class FormatComponentTest {

    @Test
    void replacesPlaceholdersInTextAndClickPayloads() {
        var message =
                (TextComponent)
                        MiniMessage.miniMessage()
                                .deserialize(
                                        "<gold>Key:"
                                            + " <click:copy_to_clipboard:'%code%'><bold>%code%</bold></click>");

        var formatted = GeneralUtil.formatComponent(message, Map.of("%code%", "ABCD2345"));

        assertEquals(
                "Key: ABCD2345", PlainTextComponentSerializer.plainText().serialize(formatted));
        var click = findClick(formatted);
        assertNotNull(click);
        assertEquals(ClickEvent.Action.COPY_TO_CLIPBOARD, click.action());
        assertEquals("ABCD2345", ((ClickEvent.Payload.Text) click.payload()).value());
    }

    private static ClickEvent findClick(Component component) {
        if (component.clickEvent() != null) return component.clickEvent();
        for (var child : component.children()) {
            var click = findClick(child);
            if (click != null) return click;
        }
        return null;
    }
}
