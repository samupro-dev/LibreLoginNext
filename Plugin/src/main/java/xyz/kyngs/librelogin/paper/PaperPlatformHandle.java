/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper;

import com.google.common.base.MoreObjects;
import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import xyz.kyngs.librelogin.api.PlatformHandle;
import xyz.kyngs.librelogin.api.server.ServerPing;
import xyz.kyngs.librelogin.lib.adventure.audience.Audience;
import xyz.kyngs.librelogin.lib.adventure.text.Component;

/** Platform abstraction handle implementation for Paper/Bukkit servers. */
public class PaperPlatformHandle implements PlatformHandle<Player, World> {

    private final PaperLibreLogin plugin;

    /**
     * Constructs a new {@link PaperPlatformHandle}.
     *
     * @param plugin the PaperLibreLogin plugin instance
     */
    public PaperPlatformHandle(PaperLibreLogin plugin) {
        this.plugin = plugin;
    }

    @Override
    public Audience getAudienceForPlayer(Player player) {
        return new PaperAudience(player);
    }

    @Override
    public UUID getUUIDForPlayer(Player player) {
        return player.getUniqueId();
    }

    @Override
    public CompletableFuture<Throwable> movePlayer(Player player, World to) {
        if (Bukkit.isPrimaryThread()) {
            return teleport(player, to);
        }
        // Older Paper versions (such as 1.16.5) reject teleportAsync started off the main thread
        // with "Asynchronous ChunkMapDistance::addPriorityTicket!".
        var result = new CompletableFuture<Throwable>();
        try {
            Bukkit.getScheduler()
                    .runTask(
                            plugin.getBootstrap(),
                            () -> teleport(player, to).thenAccept(result::complete));
        } catch (RuntimeException e) {
            result.complete(e);
        }
        return result;
    }

    private static CompletableFuture<Throwable> teleport(Player player, World to) {
        return player.teleportAsync(to.getSpawnLocation())
                .thenApply(
                        success ->
                                success ? null : (Throwable) new RuntimeException("Unknown cause"))
                .exceptionally(ex -> ex);
    }

    @Override
    public void kick(Player player, Component reason) {
        if (Bukkit.isPrimaryThread()) {
            PaperUtil.kickPlayerSafe(player, reason);
        } else {
            Bukkit.getScheduler()
                    .runTask(plugin.getBootstrap(), () -> PaperUtil.kickPlayerSafe(player, reason));
        }
    }

    @Override
    public World getServer(String name, boolean limbo) {
        var world = Bukkit.getWorld(name);

        if (world != null) {
            return world;
        }

        var file = new File(name);
        var exists = file.exists();

        if (exists) {
            plugin.getLogger().info("Loading world '" + name + "'...");
        } else {
            plugin.getLogger()
                    .info("World '" + name + "' not found on disk, creating new world...");
        }

        var creator = new WorldCreator(name);

        if (limbo) {
            creator.generator("librelogin:void");
        }

        // World creation and game rules are main-thread only; callers may be async (reload,
        // AsyncPlayerSpawnLocationEvent, /login authorization).
        var created = new World[1];
        PaperUtil.runSyncAndWait(
                () -> {
                    var existing = Bukkit.getWorld(name);
                    var newWorld = existing != null ? existing : Bukkit.createWorld(creator);

                    if (limbo && existing == null && newWorld != null) {
                        newWorld.setSpawnLocation(new Location(newWorld, 0.5, 70, 0.5));
                        PaperUtil.setBooleanGameRule(
                                newWorld, "advance_time", "doDaylightCycle", false);
                        PaperUtil.setBooleanGameRule(
                                newWorld, "spawn_phantoms", "doInsomnia", false);
                        PaperUtil.setBooleanGameRule(
                                newWorld, "spawn_mobs", "doMobSpawning", false);
                    }
                    created[0] = newWorld;
                },
                plugin);

        return created[0];
    }

    @Override
    public Class<World> getServerClass() {
        return World.class;
    }

    @Override
    public Class<Player> getPlayerClass() {
        return Player.class;
    }

    @Override
    public String getIP(Player player) {
        var addr = player.getAddress();
        if (addr == null) return "127.0.0.1";
        var inet = addr.getAddress();
        return inet != null ? inet.getHostAddress() : addr.getHostString();
    }

    @Override
    public ServerPing ping(World server) {
        return new ServerPing(Integer.MAX_VALUE);
    }

    @Override
    public Collection<World> getServers() {
        return Bukkit.getWorlds();
    }

    @Override
    public String getServerName(World server) {
        return server.getName();
    }

    @Override
    public int getConnectedPlayers(World server) {
        if (server == null) return 0;
        try {
            return server.getPlayerCount();
        } catch (LinkageError e) {
            return server.getPlayers().size();
        }
    }

    @Override
    public String getPlayersServerName(Player player) {
        return player.getWorld().getName();
    }

    @Override
    public String getPlayersVirtualHost(Player player) {
        return null;
    }

    @Override
    public String getUsernameForPlayer(Player player) {
        return player.getName();
    }

    @Override
    public String getPlatformIdentifier() {
        return "paper";
    }

    @Override
    public ProxyData getProxyData() {
        return new ProxyData(
                Bukkit.getName() + " " + Bukkit.getVersion(),
                getServers().stream().map(this::fromWorld).toList(),
                Arrays.stream(Bukkit.getPluginManager().getPlugins())
                        .map(
                                plugin ->
                                        MoreObjects.toStringHelper(plugin)
                                                .add("name", plugin.getName())
                                                .add("version", PaperUtil.getPluginVersion(plugin))
                                                .add("authors", PaperUtil.getPluginAuthors(plugin))
                                                .toString())
                        .toList(),
                plugin.getServerHandler().getLimboServers().stream().map(this::fromWorld).toList(),
                plugin.getServerHandler().getLobbyServers().values().stream()
                        .map(this::fromWorld)
                        .toList());
    }

    private String fromWorld(World world) {
        return MoreObjects.toStringHelper(world)
                .add("name", world.getName())
                .add("environment", world.getEnvironment())
                .add("difficulty", world.getDifficulty())
                .add("players", world.getPlayers().size())
                .toString();
    }
}
