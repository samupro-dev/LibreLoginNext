/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.redis;

import io.lettuce.core.pubsub.RedisPubSubListener;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import org.jetbrains.annotations.NotNull;
import xyz.kyngs.librelogin.api.Logger;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.database.provider.LibreLoginSQLDatabaseProvider;
import xyz.kyngs.librelogin.lib.adventure.text.Component;

/**
 * Handles Redis Pub/Sub messaging for real-time cluster synchronization, including distributed
 * cache invalidations and remote player kick dispatches.
 */
public class RedisPubSubHandler implements RedisPubSubListener<String, String> {

    public static final String CHANNEL_EVENTS = "librelogin:sync:events";

    private static final String MSG_INVALIDATE_USER = "INVALIDATE_USER:";
    private static final String MSG_REMOTE_KICK = "REMOTE_KICK:";

    private final AuthenticLibreLogin<?, ?> plugin;
    private final StatefulRedisPubSubConnection<String, String> pubSubConnection;
    private final String serverId;
    private final Logger logger;

    /**
     * Constructs a new {@link RedisPubSubHandler}.
     *
     * @param plugin the plugin instance
     * @param pubSubConnection the stateful pub/sub connection
     * @param serverId the unique cluster node identifier
     */
    public RedisPubSubHandler(
            AuthenticLibreLogin<?, ?> plugin,
            StatefulRedisPubSubConnection<String, String> pubSubConnection,
            String serverId) {
        this.plugin = plugin;
        this.pubSubConnection = pubSubConnection;
        this.serverId = serverId;
        this.logger = plugin.getLogger();
    }

    /** Subscribes to the synchronization broadcast channel. */
    public void subscribe() {
        pubSubConnection.addListener(this);
        pubSubConnection.async().subscribe(CHANNEL_EVENTS);
    }

    /** Unsubscribes from the synchronization broadcast channel. */
    public void unsubscribe() {
        try {
            pubSubConnection.removeListener(this);
            pubSubConnection.async().unsubscribe(CHANNEL_EVENTS);
        } catch (Throwable ignored) {
        }
    }

    /**
     * Publishes a cache invalidation request to all cluster nodes for the given user.
     *
     * @param uuid the unique identifier of the user
     */
    public void publishInvalidateUser(@NotNull UUID uuid) {
        publish(MSG_INVALIDATE_USER + uuid + ":" + serverId);
    }

    /**
     * Publishes a remote kick command to disconnect a player across the cluster.
     *
     * @param uuid the unique identifier of the player
     * @param reason the kick reason
     */
    public void publishRemoteKick(@NotNull UUID uuid, @NotNull String reason) {
        var encodedReason =
                Base64.getUrlEncoder().encodeToString(reason.getBytes(StandardCharsets.UTF_8));
        publish(MSG_REMOTE_KICK + uuid + ":" + serverId + ":" + encodedReason);
    }

    private void publish(String message) {
        try {
            pubSubConnection.async().publish(CHANNEL_EVENTS, message);
        } catch (Throwable t) {
            logger.warn("Failed to publish Redis event (" + message + "): " + t.getMessage());
        }
    }

    @Override
    public void message(String channel, String message) {
        if (!CHANNEL_EVENTS.equals(channel) || message == null || message.isBlank()) {
            return;
        }

        try {
            if (message.startsWith(MSG_INVALIDATE_USER)) {
                var payload = message.substring(MSG_INVALIDATE_USER.length());
                var parts = payload.split(":");
                if (parts.length >= 1) {
                    var senderNode = parts.length > 1 ? parts[1] : "";
                    if (serverId.equals(senderNode)) {
                        return; // Ignore self-published events
                    }
                    var targetUuid = UUID.fromString(parts[0]);
                    handleInvalidateUser(targetUuid);
                }
            } else if (message.startsWith(MSG_REMOTE_KICK)) {
                var payload = message.substring(MSG_REMOTE_KICK.length());
                var parts = payload.split(":", 3);
                if (parts.length >= 3) {
                    var senderNode = parts[1];
                    if (serverId.equals(senderNode)) {
                        return; // Ignore self-published kicks
                    }
                    var targetUuid = UUID.fromString(parts[0]);
                    String reason;
                    try {
                        reason =
                                new String(
                                        Base64.getUrlDecoder().decode(parts[2]),
                                        StandardCharsets.UTF_8);
                    } catch (IllegalArgumentException e) {
                        reason = parts[2]; // Fallback in case unencoded text was received
                    }
                    handleRemoteKick(targetUuid, reason);
                } else if (parts.length == 2) {
                    var targetUuid = UUID.fromString(parts[0]);
                    handleRemoteKick(targetUuid, parts[1]);
                }
            }
        } catch (Throwable t) {
            logger.warn(
                    "Error processing Redis pub/sub message (" + message + "): " + t.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void handleInvalidateUser(UUID uuid) {
        var dbProvider = plugin.getDatabaseProvider();
        if (dbProvider instanceof LibreLoginSQLDatabaseProvider sqlProvider) {
            sqlProvider.invalidateUserByUuid(uuid);
        }
    }

    /** Marks a remote-kick reason that is a message key, rendered by the receiving proxy. */
    public static final String MESSAGE_KEY_PREFIX = "key:";

    private void handleRemoteKick(UUID uuid, String reason) {
        if (uuid == null) {
            return;
        }
        Component message;
        if (reason != null && reason.startsWith(MESSAGE_KEY_PREFIX)) {
            message =
                    plugin.getMessages().getMessage(reason.substring(MESSAGE_KEY_PREFIX.length()));
        } else if (reason != null) {
            message = Component.text(reason);
        } else {
            message = plugin.getMessages().getMessage("kick.remote-default");
        }
        if (message == null) {
            message = Component.text("Disconnected.");
        }
        kickLocalPlayer(uuid, message);
    }

    @SuppressWarnings("unchecked")
    private <P> void kickLocalPlayer(UUID uuid, Component reason) {
        var authenticPlugin = (AuthenticLibreLogin<P, ?>) plugin;
        var player = authenticPlugin.getPlayerForUUID(uuid);
        if (player != null) {
            authenticPlugin.kickPlayer(player, reason);
        }
    }

    @Override
    public void message(String pattern, String channel, String message) {}

    @Override
    public void subscribed(String channel, long count) {
        logger.info("Subscribed to Redis cluster channel: " + channel);
    }

    @Override
    public void psubscribed(String pattern, long count) {}

    @Override
    public void unsubscribed(String channel, long count) {
        logger.info("Unsubscribed from Redis cluster channel: " + channel);
    }

    @Override
    public void punsubscribed(String pattern, long count) {}
}
