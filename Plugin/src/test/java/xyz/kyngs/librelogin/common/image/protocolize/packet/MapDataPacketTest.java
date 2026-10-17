/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.image.protocolize.packet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import org.junit.jupiter.api.Test;

/** Checks the 2FA map packet IDs against PacketEvents' independent packet tables. */
class MapDataPacketTest {

    @Test
    void everySupportedReleaseUsesPacketEventsMapDataId() {
        int checked = 0;
        for (var version : ClientVersion.values()) {
            if (!version.isRelease()
                    || version.getProtocolVersion() < ClientVersion.V_1_13.getProtocolVersion()
                    || version.getProtocolVersion() > MapDataPacket.MAX_SUPPORTED_PROTOCOL) {
                continue;
            }
            int protocol = version.getProtocolVersion();
            int expected = PacketType.Play.Server.MAP_DATA.getId(version);
            var mapping =
                    MapDataPacket.MAPPINGS.stream()
                            .filter(m -> m.inRange(protocol))
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("No mapping for " + version));
            assertEquals(expected, mapping.id(), "Map data packet ID for " + version);
            checked++;
        }
        assertTrue(checked > 30, "too few versions checked: " + checked);
    }

    @Test
    void unknownFutureProtocolsHaveNoMapping() {
        int future = MapDataPacket.MAX_SUPPORTED_PROTOCOL + 1;
        assertTrue(MapDataPacket.MAPPINGS.stream().noneMatch(m -> m.inRange(future)));
        assertEquals(
                ClientVersion.V_26_3.getProtocolVersion(), MapDataPacket.MAX_SUPPORTED_PROTOCOL);
    }
}
