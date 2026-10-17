/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.velocity;

import com.google.common.base.MoreObjects;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.PlatformHandle;
import xyz.kyngs.librelogin.api.server.ServerPing;
import xyz.kyngs.librelogin.lib.adventure.audience.Audience;
import xyz.kyngs.librelogin.lib.adventure.text.Component;

/**
 * Implementation of {@link PlatformHandle} bridging LibreLogin core functionalities with the
 * Velocity proxy platform.
 */
public class VelocityPlatformHandle implements PlatformHandle<Player, RegisteredServer> {

    private final VelocityLibreLogin plugin;

    /**
     * Constructs a new {@code VelocityPlatformHandle}.
     *
     * @param plugin the Velocity LibreLogin plugin instance
     */
    public VelocityPlatformHandle(VelocityLibreLogin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Audience getAudienceForPlayer(Player player) {
        return new VelocityAudience(player);
    }

    @Override
    public UUID getUUIDForPlayer(Player player) {
        return player.getUniqueId();
    }

    @Override
    public CompletableFuture<Throwable> movePlayer(Player player, RegisteredServer to) {
        return player.createConnectionRequest(to)
                .connect()
                .thenApply(
                        result -> {
                            if (result.isSuccessful()) {
                                return (Throwable) null;
                            }
                            return (Throwable)
                                    result.getReasonComponent()
                                            .map(
                                                    component ->
                                                            new RuntimeException(
                                                                    "Failed to move player: "
                                                                            + PlainTextComponentSerializer
                                                                                    .plainText()
                                                                                    .serialize(
                                                                                            component)))
                                            .orElseGet(
                                                    () ->
                                                            new RuntimeException(
                                                                    "Failed to move player"));
                        })
                .exceptionally(ex -> ex);
    }

    @Override
    public void kick(Player player, Component reason) {
        player.disconnect(VelocityAudience.toNative(reason));
    }

    @Override
    public RegisteredServer getServer(String name, boolean limbo) {
        return plugin.getServer().getServer(name).orElse(null);
    }

    @Override
    public Class<RegisteredServer> getServerClass() {
        return RegisteredServer.class;
    }

    @Override
    public Class<Player> getPlayerClass() {
        return Player.class;
    }

    @Override
    public String getIP(Player player) {
        var addr = player.getRemoteAddress();
        if (addr == null) return "127.0.0.1";
        var inet = addr.getAddress();
        return inet != null ? inet.getHostAddress() : addr.getHostString();
    }

    @Override
    public ServerPing ping(RegisteredServer server) {
        try {
            var pingResult = server.ping().get(3, TimeUnit.SECONDS);
            var players = pingResult.getPlayers();

            return players.map(
                            value ->
                                    new ServerPing(
                                            value.getMax() == -1
                                                    ? Integer.MAX_VALUE
                                                    : value.getMax()))
                    .orElse(null);

        } catch (Exception e) {
            if (plugin.getLogger().isDebugEnabled()) {
                plugin.getLogger()
                        .debug(
                                "Failed to ping backend server '"
                                        + server.getServerInfo().getName()
                                        + "': "
                                        + e.getMessage());
            }
            return null;
        }
    }

    @Override
    public Collection<RegisteredServer> getServers() {
        return plugin.getServer().getAllServers();
    }

    @Override
    public String getServerName(RegisteredServer server) {
        return server.getServerInfo().getName();
    }

    @Override
    public int getConnectedPlayers(RegisteredServer server) {
        return server.getPlayersConnected().size();
    }

    @Override
    public String getPlayersServerName(Player player) {
        var server = player.getCurrentServer();

        return server.map(serverConnection -> serverConnection.getServerInfo().getName())
                .orElse(null);
    }

    @Override
    public String getPlayersVirtualHost(@Nullable Player player) {
        InetSocketAddress virt = null;
        if (player != null) {
            virt = player.getVirtualHost().orElse(null);
        }
        return virt == null ? null : virt.getHostString();
    }

    @Override
    public String getUsernameForPlayer(Player player) {
        return player.getUsername();
    }

    @Override
    public String getPlatformIdentifier() {
        return "velocity";
    }

    @Override
    public ProxyData getProxyData() {
        return new ProxyData(
                plugin.getServer().getVersion().toString(),
                getServers().stream().map(Object::toString).toList(),
                plugin.getServer().getPluginManager().getPlugins().stream()
                        .map(
                                plugin ->
                                        MoreObjects.toStringHelper(
                                                        plugin.getInstance().orElse(null))
                                                .add("desc", plugin.getDescription().toString())
                                                .toString())
                        .toList(),
                plugin.getServerHandler().getLimboServers().stream().map(Object::toString).toList(),
                plugin.getServerHandler().getLobbyServers().values().stream()
                        .map(Object::toString)
                        .toList());
    }
}
