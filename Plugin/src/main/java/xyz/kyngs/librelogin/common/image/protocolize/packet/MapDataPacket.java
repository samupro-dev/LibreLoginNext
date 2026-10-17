/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.image.protocolize.packet;

import static dev.simplix.protocolize.api.mapping.AbstractProtocolMapping.rangedIdMapping;
import static dev.simplix.protocolize.api.util.ProtocolUtil.readVarInt;
import static dev.simplix.protocolize.api.util.ProtocolVersions.*;

import dev.simplix.protocolize.api.PacketDirection;
import dev.simplix.protocolize.api.mapping.ProtocolIdMapping;
import dev.simplix.protocolize.api.packet.AbstractPacket;
import dev.simplix.protocolize.api.util.ProtocolUtil;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.List;
import xyz.kyngs.librelogin.common.image.protocolize.MapData;

/** Protocolize packet implementation for sending Minecraft map item data payloads to clients. */
public class MapDataPacket extends AbstractPacket {

    // Protocol numbers newer than Protocolize 2.4.2's ProtocolVersions (which stops at 1.21.2),
    // taken from PacketEvents 2.14.0 ClientVersion.
    static final int PROTOCOL_1_21_4 = 769;
    static final int PROTOCOL_1_21_5 = 770;
    static final int PROTOCOL_1_21_8 = 772;
    static final int PROTOCOL_1_21_9 = 773;
    static final int PROTOCOL_1_21_11 = 774;
    static final int PROTOCOL_26_1 = 775;
    static final int PROTOCOL_26_2 = 776;
    static final int PROTOCOL_26_3 = 777;

    /** Newest protocol with a verified map-data packet ID; newer clients get no QR map. */
    public static final int MAX_SUPPORTED_PROTOCOL = PROTOCOL_26_3;

    /*
     * Clientbound "map item data" packet IDs, from the PacketEvents 2.14.0 ClientboundPacketType
     * tables. Every range is closed: an unknown future protocol must not receive a guessed ID
     * (the client would disconnect with a decoder error).
     */
    public static final List<ProtocolIdMapping> MAPPINGS =
            Arrays.asList(
                    rangedIdMapping(MINECRAFT_1_13, MINECRAFT_1_14_4, 0x26),
                    rangedIdMapping(MINECRAFT_1_15, MINECRAFT_1_15_2, 0x27),
                    rangedIdMapping(MINECRAFT_1_16, MINECRAFT_1_16_1, 0x26),
                    rangedIdMapping(MINECRAFT_1_16_2, MINECRAFT_1_16_5, 0x25),
                    rangedIdMapping(MINECRAFT_1_17, MINECRAFT_1_18_2, 0x27),
                    rangedIdMapping(MINECRAFT_1_19, MINECRAFT_1_19, 0x24),
                    rangedIdMapping(MINECRAFT_1_19_1, MINECRAFT_1_19_2, 0x26),
                    rangedIdMapping(MINECRAFT_1_19_3, MINECRAFT_1_19_3, 0x25),
                    rangedIdMapping(MINECRAFT_1_19_4, MINECRAFT_1_20_1, 0x29),
                    rangedIdMapping(MINECRAFT_1_20_2, MINECRAFT_1_20_4, 0x2a),
                    rangedIdMapping(MINECRAFT_1_20_5, MINECRAFT_1_21_1, 0x2c),
                    rangedIdMapping(MINECRAFT_1_21_2, PROTOCOL_1_21_4, 0x2d),
                    rangedIdMapping(PROTOCOL_1_21_5, PROTOCOL_1_21_8, 0x2c),
                    rangedIdMapping(PROTOCOL_1_21_9, PROTOCOL_1_21_11, 0x31),
                    rangedIdMapping(PROTOCOL_26_1, PROTOCOL_26_2, 0x33),
                    rangedIdMapping(PROTOCOL_26_3, PROTOCOL_26_3, 0x34));

    private int mapID;
    private byte scale;
    private MapData mapData;
    private boolean trackingPosition;
    private boolean locked;
    private IconData[] iconData;

    /**
     * Constructs a populated map data packet.
     *
     * @param mapID the numerical identifier of the map
     * @param scale the map scale level
     * @param mapData the map pixel payload and dimensions
     */
    public MapDataPacket(int mapID, byte scale, MapData mapData) {
        this.mapID = mapID;
        this.scale = scale;
        this.mapData = mapData;

        this.trackingPosition = false;
        this.iconData = new IconData[0];
        this.locked = false;
    }

    /** No-args constructor for reflection / deserialization. */
    public MapDataPacket() {}

    @Override
    public void read(ByteBuf byteBuf, PacketDirection packetDirection, int protocol) {
        mapID = readVarInt(byteBuf);
        scale = byteBuf.readByte();

        if (protocol < MINECRAFT_1_17) {
            trackingPosition = byteBuf.readBoolean();

            if (protocol >= MINECRAFT_1_14) {
                locked = byteBuf.readBoolean();
            }
        } else {
            locked = byteBuf.readBoolean();
            trackingPosition = byteBuf.readBoolean();
        }

        iconData =
                new IconData
                        [(protocol < MINECRAFT_1_17 || trackingPosition) ? readVarInt(byteBuf) : 0];

        for (int i = 0; i < iconData.length; i++) {
            iconData[i] =
                    new IconData(
                            readVarInt(byteBuf),
                            byteBuf.readByte(),
                            byteBuf.readByte(),
                            byteBuf.readByte(),
                            byteBuf.readBoolean() ? ProtocolUtil.readString(byteBuf) : null);
        }

        var columns = byteBuf.readUnsignedByte();

        if (columns <= 0) {
            mapData = new MapData(0, 0, 0, 0, new byte[0]);
        } else {
            var rows = byteBuf.readUnsignedByte();
            var x = byteBuf.readByte();
            var y = byteBuf.readByte();
            var data = ProtocolUtil.readByteArray(byteBuf);

            mapData = new MapData(columns, rows, x, y, data);
        }
    }

    @Override
    public void write(ByteBuf byteBuf, PacketDirection packetDirection, int protocol) {
        ProtocolUtil.writeVarInt(byteBuf, mapID);
        byteBuf.writeByte(scale);

        if (protocol < MINECRAFT_1_17) {
            byteBuf.writeBoolean(trackingPosition);

            if (protocol >= MINECRAFT_1_14) {
                byteBuf.writeBoolean(locked);
            }
        } else {
            byteBuf.writeBoolean(locked);
            byteBuf.writeBoolean(trackingPosition);
        }

        if (protocol < MINECRAFT_1_17 || trackingPosition) {
            ProtocolUtil.writeVarInt(byteBuf, iconData.length);

            for (IconData icon : iconData) {
                ProtocolUtil.writeVarInt(byteBuf, icon.type);
                byteBuf.writeByte(icon.x);
                byteBuf.writeByte(icon.z);
                byteBuf.writeByte(icon.direction);
                if (icon.displayName != null) {
                    byteBuf.writeBoolean(true);
                    ProtocolUtil.writeString(byteBuf, icon.displayName);
                } else {
                    byteBuf.writeBoolean(false);
                }
            }
        }

        if (mapData == null || mapData.columns() <= 0) {
            byteBuf.writeByte(0);
        } else {
            byteBuf.writeByte(mapData.columns());
            byteBuf.writeByte(mapData.rows());
            byteBuf.writeByte(mapData.posX());
            byteBuf.writeByte(mapData.posZ());
            ProtocolUtil.writeByteArray(byteBuf, mapData.data());
        }
    }

    private record IconData(int type, byte x, byte z, byte direction, String displayName) {}
}
