/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import xyz.kyngs.librelogin.api.server.ServerPing;
import xyz.kyngs.librelogin.lib.adventure.audience.Audience;
import xyz.kyngs.librelogin.lib.adventure.text.Component;

/**
 * Platform abstraction layer for interacting with underlying server and proxy implementations.
 *
 * @param <P> the platform-specific player type
 * @param <S> the platform-specific server type
 */
public interface PlatformHandle<P, S> {

    /**
     * Retrieves the Adventure {@link Audience} associated with the given player.
     *
     * @param player the player instance
     * @return the audience representing the player
     */
    Audience getAudienceForPlayer(P player);

    /**
     * Retrieves the unique identifier (UUID) for the given player.
     *
     * @param player the player instance
     * @return the UUID of the player
     */
    UUID getUUIDForPlayer(P player);

    /**
     * Connects or transfers the player to the specified server asynchronously.
     *
     * @param player the player to transfer
     * @param to the destination server
     * @return a {@link CompletableFuture} completing with null on success, or a {@link Throwable}
     *     on failure
     */
    CompletableFuture<Throwable> movePlayer(P player, S to);

    /**
     * Disconnects/kicks the player from the network with the specified reason.
     *
     * @param player the player to disconnect
     * @param reason the kick reason component
     */
    void kick(P player, Component reason);

    /**
     * Retrieves a server handle by its configured name.
     *
     * @param name the server identifier
     * @param limbo whether to include servers running in limbo mode
     * @return the server instance, or null if not found
     */
    S getServer(String name, boolean limbo);

    /**
     * Retrieves the runtime {@link Class} representing the server type {@link S}.
     *
     * @return the server class
     */
    Class<S> getServerClass();

    /**
     * Retrieves the runtime {@link Class} representing the player type {@link P}.
     *
     * @return the player class
     */
    Class<P> getPlayerClass();

    /**
     * Retrieves the remote IP address of the specified player.
     *
     * @param player the player instance
     * @return the IP address string
     */
    String getIP(P player);

    /**
     * Pings the specified server to retrieve status information.
     *
     * @param server the server instance to ping
     * @return the {@link ServerPing} containing server status
     */
    ServerPing ping(S server);

    /**
     * Retrieves all registered backend servers known to the platform.
     *
     * @return the collection of servers
     */
    Collection<S> getServers();

    /**
     * Retrieves the registered name for the given server instance.
     *
     * @param server the server instance
     * @return the name of the server
     */
    String getServerName(S server);

    /**
     * Retrieves the current number of connected players on the given server.
     *
     * @param server the server instance
     * @return the connected player count
     */
    int getConnectedPlayers(S server);

    /**
     * Retrieves the current server name the player is connected to.
     *
     * @param player the player instance
     * @return the connected server name, or null if not currently connected to a server
     */
    String getPlayersServerName(P player);

    /**
     * Retrieves the virtual host (hostname/domain) used by the player to connect.
     *
     * @param player the player instance
     * @return the virtual host string, or null if unknown
     */
    String getPlayersVirtualHost(P player);

    /**
     * Retrieves the current username of the given player.
     *
     * @param player the player instance
     * @return the username
     */
    String getUsernameForPlayer(P player);

    /**
     * Retrieves the platform implementation identifier (e.g. "velocity", "paper").
     *
     * @return the platform identifier
     */
    String getPlatformIdentifier();

    /**
     * Retrieves general metadata and configured server topology for the local proxy.
     *
     * @return the proxy data record
     */
    ProxyData getProxyData();

    /**
     * Encapsulates topology and plugin data of the local proxy instance.
     *
     * @param name the proxy instance identifier
     * @param servers list of registered server names
     * @param plugins list of loaded plugin names
     * @param limbos list of registered limbo server names
     * @param lobbies list of registered lobby server names
     */
    record ProxyData(
            String name,
            List<String> servers,
            List<String> plugins,
            List<String> limbos,
            List<String> lobbies) {}
}
