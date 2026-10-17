/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper.protocol;

import static io.github.retrooper.packetevents.util.SpigotReflectionUtil.CHANNEL_CLASS;
import static io.github.retrooper.packetevents.util.SpigotReflectionUtil.NETWORK_MANAGER_CLASS;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.util.reflection.ReflectionObject;
import io.github.retrooper.packetevents.util.SpigotReflectionUtil;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import java.util.ArrayList;
import java.util.List;

/**
 * Protocol and reflection utility methods for PacketEvents and Spigot/Paper network channel
 * management.
 */
public final class ProtocolUtil {

    private static final AttributeKey<Object> NETWORK_MANAGER_ATTR =
            AttributeKey.valueOf("librelogin_network_manager");

    private ProtocolUtil() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Retrieves the current Minecraft server version via PacketEvents.
     *
     * @return the detected {@link ServerVersion}
     */
    public static ServerVersion getServerVersion() {
        return PacketEvents.getAPI().getServerManager().getVersion();
    }

    /**
     * Extracts the Netty {@link Channel} from an internal Minecraft NetworkManager instance.
     *
     * @param networkManager the NetworkManager object
     * @return the underlying Netty {@link Channel}
     */
    public static Channel getChannel(Object networkManager) {
        ReflectionObject wrapper = new ReflectionObject(networkManager, NETWORK_MANAGER_CLASS);
        return (Channel) wrapper.readObject(0, CHANNEL_CLASS);
    }

    /**
     * Locates the Minecraft NetworkManager instance corresponding to a given Netty channel.
     *
     * @param channel the Netty channel object
     * @return the corresponding NetworkManager instance, or {@code null} if not found
     */
    public static Object findNetworkManager(Object channel) {
        if (channel instanceof Channel nettyChannel) {
            Object cached = nettyChannel.attr(NETWORK_MANAGER_ATTR).get();
            if (cached != null) {
                return cached;
            }

            Object handler = nettyChannel.pipeline().get("packet_handler");
            if (handler != null && NETWORK_MANAGER_CLASS.isAssignableFrom(handler.getClass())) {
                nettyChannel.attr(NETWORK_MANAGER_ATTR).set(handler);
                return handler;
            }

            try {
                @SuppressWarnings("unchecked")
                var typedHandler =
                        nettyChannel
                                .pipeline()
                                .get(
                                        (Class<? extends io.netty.channel.ChannelHandler>)
                                                NETWORK_MANAGER_CLASS);
                if (typedHandler != null) {
                    nettyChannel.attr(NETWORK_MANAGER_ATTR).set(typedHandler);
                    return typedHandler;
                }
            } catch (Throwable ignored) {
            }

            var managers = SpigotReflectionUtil.getNetworkManagers();
            if (managers != null) {
                // The server mutates this synchronized list from its own threads: snapshot it
                // under the list's lock before iterating.
                List<Object> snapshot;
                synchronized (managers) {
                    snapshot = new ArrayList<>(managers);
                }
                for (Object manager : snapshot) {
                    var managerChannel = (Channel) getChannel(manager);
                    if (managerChannel != null
                            && managerChannel.remoteAddress() != null
                            && managerChannel
                                    .remoteAddress()
                                    .equals(nettyChannel.remoteAddress())) {
                        nettyChannel.attr(NETWORK_MANAGER_ATTR).set(manager);
                        return manager;
                    }
                }
            }
        }
        return null;
    }
}
