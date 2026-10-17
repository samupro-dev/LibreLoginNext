/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.redis;

import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.NotNull;
import xyz.kyngs.librelogin.api.Logger;

/**
 * Handles cross-proxy/cross-server player presence registration, heartbeats, and cluster-wide
 * online status queries using Redis key-value pairs with TTL.
 */
public class RedisPresenceProvider {

    private static final String PLAYER_KEY_PREFIX = "librelogin:player:";
    private static final String NODE_PLAYERS_KEY_PREFIX = "librelogin:node:";

    private final RedisAsyncCommands<String, String> async;
    private final String serverId;
    private final int keyTtlSeconds;
    private final Logger logger;
    private final String nodePlayersKey;

    /** Deletes KEYS[1] only if this node (ARGV[1]) still owns it. */
    private static final String DELETE_IF_OWNER =
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end"
                    + " return 0";

    /**
     * Refreshes or re-creates the presence key for a local player (self-healing after a Redis
     * restart or an expired key), but never takes over a key another node now owns.
     */
    private static final String REFRESH_IF_FREE_OR_OWNER =
            "local v = redis.call('GET', KEYS[1]) "
                    + "if v == false or v == ARGV[1] then "
                    + "redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2]) return 1 end return 0";

    /**
     * Constructs a new {@link RedisPresenceProvider}.
     *
     * @param async the asynchronous Redis commands interface
     * @param serverId the unique cluster node identifier
     * @param keyTtlSeconds the TTL in seconds for presence keys
     * @param logger the logger instance
     */
    public RedisPresenceProvider(
            RedisAsyncCommands<String, String> async,
            String serverId,
            int keyTtlSeconds,
            Logger logger) {
        this.async = async;
        this.serverId = serverId;
        this.keyTtlSeconds = keyTtlSeconds;
        this.logger = logger;
        this.nodePlayersKey = NODE_PLAYERS_KEY_PREFIX + serverId + ":players";
    }

    /**
     * Registers a player as online on this cluster node.
     *
     * @param uuid the player's unique identifier
     */
    public void registerPlayer(@NotNull UUID uuid) {
        var strUuid = uuid.toString();
        var key = PLAYER_KEY_PREFIX + strUuid;
        try {
            async.set(key, serverId, SetArgs.Builder.ex(keyTtlSeconds));
            async.sadd(nodePlayersKey, strUuid);
            async.expire(nodePlayersKey, keyTtlSeconds * 2L);
        } catch (Throwable t) {
            logger.warn(
                    "Failed to register player presence in Redis for "
                            + uuid
                            + ": "
                            + t.getMessage());
        }
    }

    /**
     * Unregisters a player when disconnecting from this cluster node.
     *
     * @param uuid the player's unique identifier
     */
    public void unregisterPlayer(@NotNull UUID uuid) {
        var strUuid = uuid.toString();
        var key = PLAYER_KEY_PREFIX + strUuid;
        try {
            // Atomic: a key that another node took over in the meantime is left alone.
            async.<Long>eval(
                            DELETE_IF_OWNER, ScriptOutputType.INTEGER, new String[] {key}, serverId)
                    .exceptionally(
                            t -> {
                                logger.warn(
                                        "Failed to remove player presence in Redis for "
                                                + uuid
                                                + ": "
                                                + t.getMessage());
                                return null;
                            });
            async.srem(nodePlayersKey, strUuid);
        } catch (Throwable t) {
            logger.warn(
                    "Failed to unregister player presence in Redis for "
                            + uuid
                            + ": "
                            + t.getMessage());
        }
    }

    /**
     * Checks whether a player is online anywhere on the proxy/server cluster.
     *
     * @param uuid the player's unique identifier
     * @return {@code true} if the player's presence key exists, {@code false} otherwise
     */
    public boolean isPlayerOnline(@NotNull UUID uuid) {
        var key = PLAYER_KEY_PREFIX + uuid;
        try {
            var future = async.exists(key).toCompletableFuture();
            var count = future.get(500, TimeUnit.MILLISECONDS);
            return count != null && count > 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Asynchronously checks whether a player is online anywhere on the cluster.
     *
     * @param uuid the player's unique identifier
     * @return a {@link CompletableFuture} completing with {@code true} if online
     */
    public CompletableFuture<Boolean> isPlayerOnlineAsync(@NotNull UUID uuid) {
        var key = PLAYER_KEY_PREFIX + uuid;
        return async.exists(key)
                .toCompletableFuture()
                .thenApply(count -> count != null && count > 0)
                .exceptionally(ex -> false);
    }

    /**
     * Renews TTLs for all players currently connected to this node via pipelined asynchronous
     * commands.
     *
     * @param localPlayerUuids the collection of currently online player UUIDs
     */
    public void heartbeat(@NotNull Collection<UUID> localPlayerUuids) {
        if (localPlayerUuids.isEmpty()) {
            async.expire(nodePlayersKey, keyTtlSeconds * 2L);
            return;
        }

        try {
            var futures = new ArrayList<CompletableFuture<?>>(localPlayerUuids.size() + 1);
            for (UUID uuid : localPlayerUuids) {
                var strUuid = uuid.toString();
                var key = PLAYER_KEY_PREFIX + strUuid;
                futures.add(
                        async.<Long>eval(
                                        REFRESH_IF_FREE_OR_OWNER,
                                        ScriptOutputType.INTEGER,
                                        new String[] {key},
                                        serverId,
                                        String.valueOf(keyTtlSeconds))
                                .toCompletableFuture());
                futures.add(async.sadd(nodePlayersKey, strUuid).toCompletableFuture());
            }
            futures.add(async.expire(nodePlayersKey, keyTtlSeconds * 2L).toCompletableFuture());

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .exceptionally(
                            t -> {
                                logger.warn(
                                        "Redis heartbeat batch encountered an issue: "
                                                + t.getMessage());
                                return null;
                            });
        } catch (Throwable t) {
            logger.warn("Redis presence heartbeat encountered an error: " + t.getMessage());
        }
    }

    /** Cleans up all presence keys for this node upon server shutdown. */
    public void cleanupNode() {
        try {
            var membersFuture = async.smembers(nodePlayersKey).toCompletableFuture();
            var members = membersFuture.get(1, TimeUnit.SECONDS);
            if (members != null && !members.isEmpty()) {
                var delFutures = new ArrayList<CompletableFuture<?>>(members.size() + 1);
                for (String uuidStr : members) {
                    // Only this node's own entries: a player who moved to another proxy keeps
                    // theirs.
                    delFutures.add(
                            async.<Long>eval(
                                            DELETE_IF_OWNER,
                                            ScriptOutputType.INTEGER,
                                            new String[] {PLAYER_KEY_PREFIX + uuidStr},
                                            serverId)
                                    .toCompletableFuture());
                }
                delFutures.add(async.del(nodePlayersKey).toCompletableFuture());
                CompletableFuture.allOf(delFutures.toArray(new CompletableFuture[0]))
                        .get(1, TimeUnit.SECONDS);
            } else {
                async.del(nodePlayersKey);
            }
        } catch (Throwable ignored) {
        }
    }
}
