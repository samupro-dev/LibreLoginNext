/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.image.packetevents;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import java.awt.Color;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

class PacketEventsImageProjectorTest {

    @Test
    void supportsReleaseClientsFrom113() {
        assertTrue(PacketEventsImageProjector.supports(ClientVersion.V_1_13));
        assertTrue(PacketEventsImageProjector.supports(ClientVersion.V_1_21_5));
        assertTrue(PacketEventsImageProjector.supports(ClientVersion.V_26_3));

        assertFalse(PacketEventsImageProjector.supports(ClientVersion.V_1_12_2));
        assertFalse(PacketEventsImageProjector.supports(ClientVersion.UNKNOWN));
        assertFalse(PacketEventsImageProjector.supports(null));
    }

    @Test
    void convertsBlackAndWhitePixelsToMapColors() {
        var image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 128, 128);
        graphics.dispose();
        image.setRGB(0, 0, Color.BLACK.getRGB());

        var data = PacketEventsImageProjector.mapColors(image);
        assertEquals(128 * 128, data.length);
        assertEquals(116, data[0]);
        assertEquals(56, data[1]);
    }

    @Test
    void scalesImagesToTheMapSize() {
        var image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_RGB); // all black
        var data = PacketEventsImageProjector.mapColors(image);
        assertEquals(128 * 128, data.length);
        assertEquals(116, data[128 * 128 - 1]);
    }
}
