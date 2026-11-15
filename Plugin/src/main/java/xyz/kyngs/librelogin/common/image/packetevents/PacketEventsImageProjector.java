/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.image.packetevents;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.component.ComponentTypes;
import com.github.retrooper.packetevents.protocol.item.ItemStack;
import com.github.retrooper.packetevents.protocol.item.type.ItemTypes;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import com.github.retrooper.packetevents.protocol.player.ClientVersion;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientHeldItemChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerHeldItemChange;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerMapData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetSlot;
import java.awt.image.BufferedImage;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.image.ImageProjector;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.image.AuthenticImageProjector;

/**
 * Projects the 2FA QR code onto a map in the player's hand through PacketEvents.
 *
 * <p>Works on Paper and on Velocity with the PacketEvents plugin installed: PacketEvents resolves
 * the packet IDs and item format of every supported version, and the player object of either
 * platform is accepted. The map and the item only exist on the client; they disappear on the next
 * inventory update from the server (the player is kicked after confirming 2FA).
 */
public class PacketEventsImageProjector<P, S> extends AuthenticImageProjector<P, S>
        implements ImageProjector<P> {

    private static final int MAP_ID = 0;
    private static final int MAP_SIZE = 128;
    // First hotbar slot in the player inventory window (window 0).
    private static final int HOTBAR_FIRST_SLOT = 36;
    // Map color indexes used for the QR code (black and white).
    private static final byte DARK = 116;
    private static final byte LIGHT = 56;

    /**
     * Constructs a new PacketEvents image projector.
     *
     * @param plugin the LibreLogin plugin instance
     */
    public PacketEventsImageProjector(AuthenticLibreLogin<P, S> plugin) {
        super(plugin);
    }

    @Override
    public boolean canProject(P player) {
        var user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        return user != null && supports(user.getClientVersion());
    }

    @Override
    public void project(BufferedImage image, P player) {
        var user = PacketEvents.getAPI().getPlayerManager().getUser(player);
        if (user == null) return;

        user.sendPacket(new WrapperPlayServerSetSlot(0, 0, HOTBAR_FIRST_SLOT, mapItem()));
        // Keep the server's selected slot in sync with the client. receivePacketSilently injects
        // the packet after the decryption and decompression handlers.
        user.receivePacketSilently(new WrapperPlayClientHeldItemChange(0));
        user.sendPacket(new WrapperPlayServerHeldItemChange(0));
        user.sendPacket(
                new WrapperPlayServerMapData(
                        MAP_ID,
                        (byte) 0,
                        false,
                        false,
                        null,
                        MAP_SIZE,
                        MAP_SIZE,
                        0,
                        0,
                        mapColors(image)));
    }

    /**
     * Checks whether a client can show the QR code map.
     *
     * @param version the client version, or {@code null} if not known yet
     * @return {@code true} for release clients from 1.13, where the map ID is an item tag
     */
    static boolean supports(@Nullable ClientVersion version) {
        return version != null
                && version.isRelease()
                && version.isNewerThanOrEquals(ClientVersion.V_1_13);
    }

    // The map ID is an NBT tag before 1.20.5 and a component since; PacketEvents writes the one
    // matching the version the packet is encoded for, so both are set.
    // Needs the running PacketEvents API (ItemStack reads the server version), so not unit tested.
    private static ItemStack mapItem() {
        var item =
                ItemStack.builder()
                        .type(ItemTypes.FILLED_MAP)
                        .amount(1)
                        .nbt("map", new NBTInt(MAP_ID))
                        .build();
        item.setComponent(ComponentTypes.MAP_ID, MAP_ID);
        return item;
    }

    /**
     * Converts an image to map colors: black pixels become dark, every other pixel light.
     *
     * @param image the image, scaled to 128x128 if needed
     * @return the 128x128 map color data
     */
    static byte[] mapColors(BufferedImage image) {
        if (image.getWidth() != MAP_SIZE || image.getHeight() != MAP_SIZE) {
            var resized = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_RGB);
            var graphics = resized.createGraphics();
            graphics.drawImage(image, 0, 0, MAP_SIZE, MAP_SIZE, null);
            graphics.dispose();
            image = resized;
        }
        int[] pixels = image.getRGB(0, 0, MAP_SIZE, MAP_SIZE, null, 0, MAP_SIZE);
        byte[] data = new byte[pixels.length];
        for (int i = 0; i < pixels.length; i++) {
            data[i] = pixels[i] == 0xFF000000 ? DARK : LIGHT;
        }
        return data;
    }
}
