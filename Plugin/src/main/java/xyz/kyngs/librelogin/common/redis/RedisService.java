/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.redis;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.ClientOptions.DisconnectedBehavior;
import io.lettuce.core.RedisChannelHandler;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisConnectionStateListener;
import io.lettuce.core.RedisURI;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.protocol.ProtocolVersion;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.DefaultClientResources;
import io.lettuce.core.resource.Delay;
import java.net.SocketAddress;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.Logger;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;

/**
 * Manages the native Lettuce Redis client connection lifecycle, presence provider, and pub/sub
 * messaging for multi-proxy/multi-server clustering.
 */
public class RedisService {

    private final AuthenticLibreLogin<?, ?> plugin;
    private final Logger logger;
    private final String serverId;
    private final int keyTtlSeconds;
    private final int heartbeatIntervalSeconds;

    private ClientResources clientResources;
    private RedisClient redisClient;
    private StatefulRedisConnection<String, String> connection;
    private StatefulRedisPubSubConnection<String, String> pubSubConnection;
    private RedisPresenceProvider presenceProvider;
    private RedisPubSubHandler pubSubHandler;
    private volatile boolean connected;

    /**
     * Constructs a new {@link RedisService} instance.
     *
     * @param plugin the plugin instance
     */
    public RedisService(AuthenticLibreLogin<?, ?> plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();

        this.serverId = "node-" + UUID.randomUUID().toString().substring(0, 8);
        var config = plugin.getConfiguration();
        var heartbeatConfig = config.get(ConfigurationKeys.REDIS_HEARTBEAT_INTERVAL_SECONDS);
        this.heartbeatIntervalSeconds = heartbeatConfig != null ? Math.max(5, heartbeatConfig) : 10;
        var ttlConfig = config.get(ConfigurationKeys.REDIS_KEY_TTL_SECONDS);
        this.keyTtlSeconds =
                ttlConfig != null
                        ? Math.max(heartbeatIntervalSeconds + 10, ttlConfig)
                        : (heartbeatIntervalSeconds + 20);
    }

    /**
     * Initializes the Lettuce Redis client and opens stateful commands and pub/sub connections.
     *
     * @return {@code true} if connection succeeded, {@code false} otherwise
     */
    public boolean connect() {
        try {
            var config = plugin.getConfiguration();
            var host = config.get(ConfigurationKeys.REDIS_HOST);
            var portVal = config.get(ConfigurationKeys.REDIS_PORT);
            var port = portVal != null && portVal > 0 ? portVal : 6379;
            var username = config.get(ConfigurationKeys.REDIS_USERNAME);
            var password = config.get(ConfigurationKeys.REDIS_PASSWORD);
            var dbVal = config.get(ConfigurationKeys.REDIS_DATABASE);
            var database = dbVal != null ? Math.max(0, dbVal) : 0;
            var sslVal = config.get(ConfigurationKeys.REDIS_SSL);
            var ssl = Boolean.TRUE.equals(sslVal);
            var timeoutVal = config.get(ConfigurationKeys.REDIS_TIMEOUT_MS);
            var timeoutMs = timeoutVal != null ? Math.max(500, timeoutVal) : 5000;

            var builder =
                    RedisURI.builder()
                            .withHost(host != null && !host.isBlank() ? host.trim() : "127.0.0.1")
                            .withPort(port)
                            .withDatabase(database)
                            .withSsl(ssl)
                            .withClientName("LibreLogin-Next-" + serverId)
                            .withTimeout(Duration.ofMillis(timeoutMs));

            if (username != null && !username.isBlank()) {
                builder.withAuthentication(username.trim(), password != null ? password : "");
            } else if (password != null && !password.isBlank()) {
                builder.withPassword(password.toCharArray());
            }

            var redisUri = builder.build();

            var threadCounter = new AtomicInteger(1);
            clientResources =
                    DefaultClientResources.builder()
                            .ioThreadPoolSize(2)
                            .computationThreadPoolSize(2)
                            .reconnectDelay(Delay.decorrelatedJitter())
                            .threadFactoryProvider(
                                    poolName ->
                                            (ThreadFactory)
                                                    runnable -> {
                                                        var thread =
                                                                new Thread(
                                                                        runnable,
                                                                        "LibreLogin-Next-Lettuce-"
                                                                                + poolName
                                                                                + "-"
                                                                                + threadCounter
                                                                                        .getAndIncrement());
                                                        thread.setDaemon(true);
                                                        return thread;
                                                    })
                            .build();

            redisClient = RedisClient.create(clientResources, redisUri);

            redisClient.setOptions(
                    ClientOptions.builder()
                            .autoReconnect(true)
                            .protocolVersion(ProtocolVersion.RESP3)
                            .disconnectedBehavior(DisconnectedBehavior.REJECT_COMMANDS)
                            .timeoutOptions(TimeoutOptions.enabled(Duration.ofMillis(timeoutMs)))
                            .socketOptions(
                                    SocketOptions.builder()
                                            .connectTimeout(Duration.ofSeconds(3))
                                            .keepAlive(true)
                                            .tcpNoDelay(true)
                                            .build())
                            .build());

            redisClient.addListener(
                    new RedisConnectionStateListener() {
                        @Override
                        public void onRedisConnected(
                                RedisChannelHandler<?, ?> conn, SocketAddress socketAddress) {
                            connected = true;
                            logger.info("Redis connection established with " + socketAddress);
                        }

                        @Override
                        public void onRedisDisconnected(RedisChannelHandler<?, ?> conn) {
                            connected = false;
                            logger.warn(
                                    "Redis connection disconnected! Falling back to local"
                                            + " standalone mode.");
                        }

                        @Override
                        public void onRedisExceptionCaught(
                                RedisChannelHandler<?, ?> conn, Throwable cause) {
                            logger.warn("Redis connection exception: " + cause.getMessage());
                        }
                    });

            connection = redisClient.connect();
            pubSubConnection = redisClient.connectPubSub();

            presenceProvider =
                    new RedisPresenceProvider(connection.async(), serverId, keyTtlSeconds, logger);
            pubSubHandler = new RedisPubSubHandler(plugin, pubSubConnection, serverId);
            pubSubHandler.subscribe();

            connected = true;
            logger.info(
                    "Connected to Redis server via Lettuce ("
                            + serverId
                            + ", db: "
                            + redisUri.getDatabase()
                            + ")");
            return true;
        } catch (Throwable t) {
            logger.error("Failed to connect to Redis: " + t.getMessage());
            connected = false;
            return false;
        }
    }

    /**
     * Checks whether the Redis connection is active and operational.
     *
     * @return {@code true} if connected, {@code false} otherwise
     */
    public boolean isConnected() {
        return connected && connection != null && connection.isOpen();
    }

    /**
     * Retrieves the presence provider for tracking online players.
     *
     * @return the {@link RedisPresenceProvider}, or {@code null} if disconnected
     */
    @Nullable
    public RedisPresenceProvider getPresenceProvider() {
        return presenceProvider;
    }

    /**
     * Retrieves the pub/sub event handler.
     *
     * @return the {@link RedisPubSubHandler}, or {@code null} if disconnected
     */
    @Nullable
    public RedisPubSubHandler getPubSubHandler() {
        return pubSubHandler;
    }

    /**
     * Returns the unique cluster node identifier.
     *
     * @return the server identifier
     */
    public String getServerId() {
        return serverId;
    }

    /**
     * Returns the heartbeat interval in seconds.
     *
     * @return interval in seconds
     */
    public int getHeartbeatIntervalSeconds() {
        return heartbeatIntervalSeconds;
    }

    /** Shuts down all Redis connections and resources cleanly. */
    public void shutdown() {
        connected = false;
        if (pubSubHandler != null) {
            try {
                pubSubHandler.unsubscribe();
            } catch (Throwable ignored) {
            }
        }
        if (presenceProvider != null) {
            try {
                presenceProvider.cleanupNode();
            } catch (Throwable ignored) {
            }
        }
        if (pubSubConnection != null) {
            try {
                pubSubConnection.close();
            } catch (Throwable ignored) {
            }
        }
        if (connection != null) {
            try {
                connection.close();
            } catch (Throwable ignored) {
            }
        }
        if (redisClient != null) {
            try {
                redisClient.shutdown(Duration.ofMillis(200), Duration.ofMillis(500));
            } catch (Throwable ignored) {
            }
        }
        if (clientResources != null) {
            try {
                clientResources.shutdown(100, 300, TimeUnit.MILLISECONDS);
            } catch (Throwable ignored) {
            }
        }
    }
}
