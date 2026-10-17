/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.server;

import com.google.common.collect.Multimap;
import java.util.Collection;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.event.exception.EventCancelledException;

/**
 * Service managing destination routing, server selection, and lobby/limbo registries.
 *
 * @param <P> the platform-specific player type
 * @param <S> the platform-specific server type
 */
public interface ServerHandler<P, S> {

    /**
     * Retrieves the latest cached or polled ping information for a server.
     *
     * @param server the server instance to ping
     * @return the {@link ServerPing} status data, or null if offline/unavailable
     */
    ServerPing getLatestPing(S server);

    /**
     * Selects an optimal lobby server to route the player to based on load balancing and routing
     * rules.
     *
     * @param user the user database profile, or null if unavailable
     * @param player the player being routed
     * @param remember whether to respect the 'remember last server' setting
     * @param fallback whether this selection is for a fallback destination (e.g. after a kick)
     * @return the selected lobby server instance, or null if no candidate server is available
     * @throws EventCancelledException if the lobby selection event is cancelled by a listener
     */
    S chooseLobbyServer(@Nullable User user, P player, boolean remember, boolean fallback)
            throws EventCancelledException;

    /**
     * Selects an optimal lobby server to route the player to based on load balancing and routing
     * rules.
     *
     * @param user the user database profile, or null if unavailable
     * @param player the player being routed
     * @param remember whether to respect the 'remember last server' setting
     * @return the selected lobby server instance, or null if no candidate server is available
     * @throws EventCancelledException if the lobby selection event is cancelled by a listener
     */
    S chooseLobbyServer(@Nullable User user, P player, boolean remember)
            throws EventCancelledException;

    /**
     * Selects an optimal limbo server for an unauthenticated player.
     *
     * @param user the user database profile
     * @param player the player being routed
     * @return the selected limbo server instance, or null if no candidate server is available
     */
    S chooseLimboServer(User user, P player);

    /**
     * Gets the multimap of registered lobby servers grouped by host key.
     *
     * <p>Uses Guava's {@link Multimap}, which Paper and Velocity both provide at runtime. Prefer
     * {@link #getLobbyServerMap()} to avoid depending on Guava.
     *
     * @return multimap of forced hosts to lobby server instances
     */
    Multimap<String, S> getLobbyServers();

    /**
     * Gets an immutable snapshot of the registered lobby servers grouped by host key, without
     * exposing Guava types.
     *
     * @return map of forced hosts to lobby server instances
     */
    default java.util.Map<String, java.util.Collection<S>> getLobbyServerMap() {
        var lobbies = getLobbyServers();
        synchronized (lobbies) {
            var copy = new java.util.LinkedHashMap<String, java.util.Collection<S>>();
            lobbies.asMap()
                    .forEach((host, servers) -> copy.put(host, java.util.List.copyOf(servers)));
            return java.util.Collections.unmodifiableMap(copy);
        }
    }

    /**
     * Gets the collection of registered limbo servers.
     *
     * @return collection of limbo server instances
     */
    Collection<S> getLimboServers();

    /**
     * Registers a new lobby server associated with a specific forced host.
     *
     * @param server the server instance to register as a lobby
     * @param forcedHost the virtual host key to associate the lobby with
     */
    void registerLobbyServer(S server, String forcedHost);

    /**
     * Registers a new default lobby server associated with the root host.
     *
     * @param server the server instance to register as a lobby
     */
    default void registerLobbyServer(S server) {
        registerLobbyServer(server, "root");
    }

    /**
     * Registers a new limbo server.
     *
     * @param server the server instance to register as a limbo
     */
    void registerLimboServer(S server);
}
