/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.image.protocolize;

import dev.simplix.protocolize.api.PacketDirection;
import dev.simplix.protocolize.api.Protocol;
import dev.simplix.protocolize.api.Protocolize;
import dev.simplix.protocolize.api.item.ItemStack;
import dev.simplix.protocolize.api.item.component.MapIdComponent;
import dev.simplix.protocolize.api.packet.AbstractPacket;
import dev.simplix.protocolize.api.providers.ModuleProvider;
import dev.simplix.protocolize.api.util.ProtocolVersions;
import dev.simplix.protocolize.data.ItemType;
import dev.simplix.protocolize.data.packets.HeldItemChange;
import dev.simplix.protocolize.data.packets.SetSlot;
import java.awt.image.BufferedImage;
import xyz.kyngs.librelogin.api.image.ImageProjector;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.image.AuthenticImageProjector;
import xyz.kyngs.librelogin.common.image.protocolize.packet.MapDataPacket;

/**
 * Image projector implementation that utilizes Protocolize packet injection to send map data
 * packets directly to the client connection for displaying 2FA QR codes on handheld maps.
 *
 * @param <P> the platform-specific player type
 * @param <S> the platform-specific server type
 */
public class ProtocolizeImageProjector<P, S> extends AuthenticImageProjector<P, S>
        implements ImageProjector<P> {

    /**
     * Constructs a new Protocolize image projector.
     *
     * @param plugin the LibreLogin plugin instance
     */
    public ProtocolizeImageProjector(AuthenticLibreLogin<P, S> plugin) {
        super(plugin);
    }

    /**
     * Checks whether the runtime Protocolize version is compatible with this image projector.
     *
     * @return {@code true} if compatible, {@code false} otherwise
     */
    public boolean compatible() {
        if (Protocolize.version().equals("2.2.2")) {
            return false;
        }
        try {
            Class.forName("dev.simplix.protocolize.api.item.component.MapIdComponent");
            return true;
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    @Override
    public void enable() {
        Protocolize.getService(ModuleProvider.class).registerModule(new ProtocolizeImageModule());
    }

    /**
     * Projects a buffered image onto an in-game map held by the specified player. Note: This
     * implementation is optimized for high-contrast monochrome patterns (such as QR codes).
     *
     * @param image the image to render
     * @param player the target player
     */
    @Override
    public void project(BufferedImage image, P player) {
        var id = platformHandle.getUUIDForPlayer(player);
        if (id == null) return;

        var protocolize = Protocolize.playerProvider().player(id);
        if (protocolize == null) return;

        var protocol = protocolize.protocolVersion();
        var item = new ItemStack(ItemType.FILLED_MAP, 1, (short) 0);

        if (protocol >= ProtocolVersions.MINECRAFT_1_20_5) {
            item.addComponent(MapIdComponent.create(0));
        } else if (protocol >= ProtocolVersions.MINECRAFT_1_17) {
            @SuppressWarnings("deprecation")
            var nbt = item.nbtData();
            nbt.putInt("map", 0);
        }

        protocolize.sendPacket(new SetSlot().slot((short) 36).itemStack(item));
        protocolize.sendPacketToServer(new HeldItemChange().newSlot((short) 0));
        protocolize.sendPacket(new HeldItemChange().newSlot((short) 0));

        if (image.getWidth() != 128 || image.getHeight() != 128) {
            int imageType =
                    image.getType() == BufferedImage.TYPE_CUSTOM
                            ? BufferedImage.TYPE_INT_RGB
                            : image.getType();
            var resized = new BufferedImage(128, 128, imageType);

            var graphics = resized.createGraphics();
            graphics.drawImage(
                    image, 0, 0, 128, 128, 0, 0, image.getWidth(), image.getHeight(), null);
            graphics.dispose();

            image = resized;
        }

        int[] pixels =
                image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth());
        byte[] data = new byte[pixels.length];

        for (int i = 0; i < pixels.length; i++) {
            data[i] = (byte) (pixels[i] == -16777216 ? 116 : 56);
        }

        protocolize.sendPacket(new MapDataPacket(0, (byte) 0, new MapData(128, 128, 0, 0, data)));
    }

    @Override
    public boolean canProject(P player) {
        var id = platformHandle.getUUIDForPlayer(player);
        if (id == null) return false;

        var protocolize = Protocolize.playerProvider().player(id);
        if (protocolize == null) return false;

        var protocol = protocolize.protocolVersion();
        // Protocolize throws when it has no mapping for a packet at the client's protocol (for
        // example SetSlot for 1.21.11 in Protocolize 2.4.2), so every packet used by project()
        // must be encodable for this client.
        return protocol >= ProtocolVersions.MINECRAFT_1_13
                && protocol <= MapDataPacket.MAX_SUPPORTED_PROTOCOL
                && encodable(SetSlot.class, PacketDirection.CLIENTBOUND, protocol)
                && encodable(HeldItemChange.class, PacketDirection.CLIENTBOUND, protocol)
                && encodable(HeldItemChange.class, PacketDirection.SERVERBOUND, protocol)
                && encodable(MapDataPacket.class, PacketDirection.CLIENTBOUND, protocol);
    }

    private static boolean encodable(
            Class<? extends AbstractPacket> packet, PacketDirection direction, int protocol) {
        try {
            return Protocolize.protocolRegistration()
                            .createPacket(packet, Protocol.PLAY, direction, protocol)
                    != null;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
