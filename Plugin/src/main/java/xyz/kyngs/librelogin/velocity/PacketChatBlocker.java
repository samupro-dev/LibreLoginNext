/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.velocity;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.client.WrapperPlayClientChatCommand;
import com.velocitypowered.api.proxy.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Drops the chat messages and the disallowed commands of players who are not logged in, before
 * Velocity handles them.
 *
 * <p>Velocity cannot deny a signed chat message or a signed command (1.19.1+ clients with a Mojang
 * chat session): {@code PlayerChatEvent} and {@code CommandExecuteEvent} denials disconnect the
 * player with "illegal protocol state" instead. Dropping the packet here keeps the player
 * connected. {@link Blockers} still handles the unsigned cases and proxies without PacketEvents.
 */
public class PacketChatBlocker implements PacketListener {

    private final VelocityLibreLogin plugin;
    private volatile @Nullable PacketListenerCommon registered;

    /**
     * Creates the blocker.
     *
     * @param plugin the Velocity plugin instance
     */
    public PacketChatBlocker(VelocityLibreLogin plugin) {
        this.plugin = plugin;
    }

    /**
     * Creates and registers a blocker.
     *
     * @param plugin the Velocity plugin instance
     * @return the action that unregisters it
     */
    static Runnable start(VelocityLibreLogin plugin) {
        var blocker = new PacketChatBlocker(plugin);
        blocker.register();
        return blocker::unregister;
    }

    /** Starts dropping the packets. */
    public void register() {
        registered =
                PacketEvents.getAPI()
                        .getEventManager()
                        .registerListener(this, PacketListenerPriority.NORMAL);
    }

    /** Stops dropping the packets; PacketEvents outlives this plugin. */
    public void unregister() {
        var listener = registered;
        registered = null;
        if (listener != null) PacketEvents.getAPI().getEventManager().unregisterListener(listener);
    }

    @Override
    public void onPacketReceive(PacketReceiveEvent event) {
        var type = event.getPacketType();
        boolean chat = type == PacketType.Play.Client.CHAT_MESSAGE;
        if (!chat && type != PacketType.Play.Client.CHAT_COMMAND) return;
        if (!(event.getPlayer() instanceof Player player)) return;

        var authorization = plugin.getAuthorizationProvider();
        if (authorization.isAuthorized(player) && !authorization.isAwaiting2FA(player)) return;
        if (!chat
                && Blockers.isAllowedWhileUnauthorized(
                        new WrapperPlayClientChatCommand(event).getCommand(),
                        plugin.getConfiguration())) {
            return;
        }

        event.setCancelled(true);
        var message = plugin.getMessages().getMessage("error.not-authorized");
        if (message != null) player.sendMessage(VelocityAudience.toNative(message));
    }
}
