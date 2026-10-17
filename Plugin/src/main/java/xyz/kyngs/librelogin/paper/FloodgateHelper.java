/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper;

import com.github.retrooper.packetevents.event.PacketReceiveEvent;
import com.github.retrooper.packetevents.wrapper.login.client.WrapperLoginClientLoginStart;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.util.AttributeKey;
import org.geysermc.floodgate.api.player.FloodgatePlayer;

/** Helper utility for integrating Geyser/Floodgate Bedrock authentication tasks on Paper. */
public class FloodgateHelper {

    private static final AttributeKey<String> KICK_MESSAGE_ATTRIBUTE =
            AttributeKey.valueOf("floodgate-kick-message");
    private static final AttributeKey<FloodgatePlayer> FLOODGATE_PLAYER_ATTRIBUTE =
            AttributeKey.valueOf("floodgate-player");

    /**
     * Processes Floodgate authentication tasks for an incoming login packet.
     *
     * @param event the packet receive event containing connection channel information
     * @param packet the login start packet wrapper
     * @return {@code false} if the player connection was closed or kicked; {@code true} otherwise
     */
    protected boolean processFloodgateTasks(
            PacketReceiveEvent event, WrapperLoginClientLoginStart packet) {
        FloodgatePlayer floodgatePlayer = getFloodgatePlayer(event.getChannel());
        if (floodgatePlayer == null) {
            return true;
        }

        if (!(event.getChannel() instanceof Channel channel)) {
            return true;
        }

        String kickMessage = channel.attr(KICK_MESSAGE_ATTRIBUTE).get();
        if (kickMessage != null) {
            event.getUser().closeConnection();
            return false;
        }

        String username = floodgatePlayer.getCorrectUsername();
        packet.setUsername(username);

        ChannelHandler floodgateHandler = channel.pipeline().get("floodgate_data_handler");
        if (floodgateHandler != null) {
            channel.pipeline().remove(floodgateHandler);
        }

        return true;
    }

    /**
     * Returns whether this connection was authenticated by Floodgate.
     *
     * @param channel the Netty channel object
     * @return {@code true} if the channel carries a Floodgate player
     */
    protected boolean isFloodgateConnection(Object channel) {
        return getFloodgatePlayer(channel) != null;
    }

    /**
     * Retrieves the {@link FloodgatePlayer} attached to the channel attributes.
     *
     * @param channel the Netty channel object
     * @return the associated {@link FloodgatePlayer}, or {@code null} if absent
     */
    private FloodgatePlayer getFloodgatePlayer(Object channel) {
        if (!(channel instanceof Channel nettyChannel)) {
            return null;
        }
        return nettyChannel.attr(FLOODGATE_PLAYER_ATTRIBUTE).get();
    }
}
