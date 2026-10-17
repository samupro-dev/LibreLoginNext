/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.server;

import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.IGNORE_MAX_PLAYERS_FROM_BACKEND_PING;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.LIMBO;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.LOBBY;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.PING_SERVERS;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.REMEMBER_LAST_SERVER;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.SetMultimap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.event.exception.EventCancelledException;
import xyz.kyngs.librelogin.api.server.ServerHandler;
import xyz.kyngs.librelogin.api.server.ServerPing;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.event.events.AuthenticLimboServerChooseEvent;
import xyz.kyngs.librelogin.common.event.events.AuthenticLobbyServerChooseEvent;
import xyz.kyngs.librelogin.common.util.CancellableTask;

/**
 * Standard implementation of {@link ServerHandler}. Manages backend server routing, server ping
 * caching, and load balancing between available lobby and Limbo instances.
 *
 * @param <P> the platform-specific player type
 * @param <S> the platform-specific server type
 */
public class AuthenticServerHandler<P, S> implements ServerHandler<P, S> {

    private final LoadingCache<S, Optional<ServerPing>> pingCache;
    private final AuthenticLibreLogin<P, S> plugin;
    // Replaced as a whole on reload, so routing never sees a half-built list.
    private volatile Collection<S> limboServers;
    private volatile SetMultimap<String, S> lobbyServers;
    private final CancellableTask pingRefreshTask;
    // Pings block (up to seconds each); keep them off the JVM-wide ForkJoin common pool.
    private final ExecutorService pingExecutor =
            Executors.newFixedThreadPool(
                    2,
                    runnable -> {
                        var thread = new Thread(runnable, "LibreLoginNext-Ping");
                        thread.setDaemon(true);
                        return thread;
                    });

    /**
     * Constructs a new server handler and initializes ping caches and configured servers.
     *
     * @param plugin the LibreLogin plugin instance
     */
    public AuthenticServerHandler(AuthenticLibreLogin<P, S> plugin) {
        this.plugin = plugin;
        this.lobbyServers = Multimaps.synchronizedSetMultimap(HashMultimap.create());
        this.limboServers = new CopyOnWriteArrayList<>();

        this.pingCache =
                Caffeine.newBuilder()
                        .executor(pingExecutor)
                        .expireAfterWrite(30, TimeUnit.SECONDS)
                        .maximumSize(100)
                        .build(
                                server -> {
                                    if (!plugin.getConfiguration().get(PING_SERVERS)) {
                                        return Optional.of(new ServerPing(Integer.MAX_VALUE));
                                    }

                                    if (plugin.getLogger().isDebugEnabled()) {
                                        plugin.getLogger()
                                                .debug("Pinging backend server: " + server);
                                    }
                                    var ping = plugin.getPlatformHandle().ping(server);
                                    if (plugin.getLogger().isDebugEnabled()) {
                                        plugin.getLogger()
                                                .debug("Ping response for " + server + ": " + ping);
                                    }

                                    if (ping == null) return Optional.empty();
                                    return Optional.of(
                                            plugin.getConfiguration()
                                                            .get(
                                                                    IGNORE_MAX_PLAYERS_FROM_BACKEND_PING)
                                                    ? new ServerPing(Integer.MAX_VALUE)
                                                    : ping);
                                });

        pingRefreshTask =
                plugin.repeat(() -> pingCache.refreshAll(pingCache.asMap().keySet()), 10000, 10000);

        if (plugin.getConfiguration().get(PING_SERVERS)) {
            plugin.getLogger().info("Pinging registered backend servers...");
        }

        reload();

        if (plugin.getLogger().isDebugEnabled()) {
            plugin.getLogger().debug("Registered server destinations:");
            for (S server : plugin.getPlatformHandle().getServers()) {
                plugin.getLogger()
                        .debug(
                                " - "
                                        + plugin.getPlatformHandle().getServerName(server)
                                        + " ("
                                        + server
                                        + ")");
            }
        }

        if (plugin.getConfiguration().get(PING_SERVERS)) {
            plugin.getLogger().info("Initial backend server ping check completed.");
        }
    }

    /**
     * Reloads configured Limbo and Lobby servers from the active configuration and clears stale
     * ping entries.
     */
    public void reload() {
        // Build the new routing tables completely (this may block on pings), then swap them in.
        // Players keep being routed with the previous tables meanwhile, and a failure while
        // building leaves the previous tables in place.
        var handle = plugin.getPlatformHandle();
        Collection<S> newLimbo = new CopyOnWriteArrayList<>();
        SetMultimap<String, S> newLobby = Multimaps.synchronizedSetMultimap(HashMultimap.create());

        for (String limbo : plugin.getConfiguration().get(LIMBO)) {
            var server = handle.getServer(limbo, true);
            if (server != null) {
                pingCache.invalidate(server);
                getLatestPing(server);
                newLimbo.add(server);
            } else {
                plugin.getLogger()
                        .warn("Configured Limbo server/world '" + limbo + "' was not found!");
            }
        }

        plugin.getConfiguration()
                .get(LOBBY)
                .forEach(
                        (forced, server) -> {
                            var s = handle.getServer(server, false);
                            if (s != null) {
                                pingCache.invalidate(s);
                                getLatestPing(s);
                                newLobby.put(forced, s);
                            } else {
                                plugin.getLogger()
                                        .warn(
                                                "Configured Lobby server/world '"
                                                        + server
                                                        + "' was not found!");
                            }
                        });

        limboServers = newLimbo;
        lobbyServers = newLobby;
    }

    /** Shuts down background ping refresh tasks and clears the ping cache. */
    public void shutdown() {
        if (pingRefreshTask != null) {
            pingRefreshTask.cancel();
        }
        pingCache.invalidateAll();
        pingExecutor.shutdownNow();
    }

    @Override
    public ServerPing getLatestPing(S server) {
        return pingCache.get(server).orElse(null);
    }

    @Override
    public S chooseLobbyServer(@Nullable User user, P player, boolean remember, boolean fallback) {
        return chooseLobbyServerInternal(user, player, remember, fallback);
    }

    /**
     * Internal routine to evaluate and select the best available lobby server for a player.
     *
     * @param user the database user, if available
     * @param player the platform player instance
     * @param remember whether to attempt reconnecting to the last remembered server
     * @param fallback whether this resolution is a fallback selection (or null if unspecified)
     * @return the selected server instance, or {@code null} if no viable server was found
     */
    public S chooseLobbyServerInternal(
            @Nullable User user, P player, boolean remember, Boolean fallback) {
        if (user != null && remember && plugin.getConfiguration().get(REMEMBER_LAST_SERVER)) {
            var last = user.getLastServer();

            if (last != null) {
                var server = plugin.getPlatformHandle().getServer(last, false);
                if (server != null) {
                    var ping = getLatestPing(server);
                    if (ping != null
                            && ping.maxPlayers()
                                    > plugin.getPlatformHandle().getConnectedPlayers(server)) {
                        return server;
                    }
                }
            }
        }

        var event = new AuthenticLobbyServerChooseEvent<>(user, player, plugin, fallback);

        plugin.getEventProvider().fire(plugin.getEventTypes().lobbyServerChoose, event);

        if (event.isCancelled()) {
            throw new EventCancelledException();
        }

        if (event.getServer() != null) {
            return event.getServer();
        }

        var virtual = plugin.getPlatformHandle().getPlayersVirtualHost(player);

        if (virtual != null) {
            plugin.getLogger()
                    .debug(
                            "Virtual host for player {}: {}",
                            plugin.getPlatformHandle().getUsernameForPlayer(player),
                            virtual);
        }

        List<S> serverList;
        var lobbies = lobbyServers;
        synchronized (lobbies) {
            serverList =
                    new ArrayList<>(virtual == null ? lobbies.get("root") : lobbies.get(virtual));
            if (serverList.isEmpty()) {
                serverList = new ArrayList<>(lobbies.get("root"));
            }
        }

        return serverList.stream()
                .filter(
                        server -> {
                            var ping = getLatestPing(server);
                            return ping != null
                                    && ping.maxPlayers()
                                            > plugin.getPlatformHandle()
                                                    .getConnectedPlayers(server);
                        })
                .min(
                        Comparator.comparingInt(
                                o -> plugin.getPlatformHandle().getConnectedPlayers(o)))
                .orElse(null);
    }

    @Override
    public S chooseLobbyServer(@Nullable User user, P player, boolean remember) {
        return chooseLobbyServerInternal(user, player, remember, null);
    }

    @Override
    public S chooseLimboServer(User user, P player) {
        var event = new AuthenticLimboServerChooseEvent<>(user, player, plugin);

        plugin.getEventProvider().fire(plugin.getEventTypes().limboServerChoose, event);

        if (event.getServer() != null) {
            return event.getServer();
        }

        return limboServers.stream()
                .filter(
                        server -> {
                            var ping = getLatestPing(server);
                            return ping != null
                                    && ping.maxPlayers()
                                            > plugin.getPlatformHandle()
                                                    .getConnectedPlayers(server);
                        })
                .min(
                        Comparator.comparingInt(
                                o -> plugin.getPlatformHandle().getConnectedPlayers(o)))
                .orElse(null);
    }

    @Override
    public Multimap<String, S> getLobbyServers() {
        return lobbyServers;
    }

    @Override
    public Collection<S> getLimboServers() {
        return limboServers;
    }

    @Override
    public void registerLobbyServer(S server, String forcedHost) {
        getLatestPing(server);
        lobbyServers.put(forcedHost, server);
    }

    @Override
    public void registerLimboServer(S server) {
        getLatestPing(server);
        limboServers.add(server);
    }
}
