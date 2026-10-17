/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper;

import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.DEBUG;

import co.aikar.commands.BukkitCommandIssuer;
import co.aikar.commands.CommandIssuer;
import co.aikar.commands.CommandManager;
import co.aikar.commands.PaperCommandManager;
import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerCommon;
import java.io.File;
import java.io.InputStream;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import xyz.kyngs.librelogin.api.Logger;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.event.exception.EventCancelledException;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.JULLogger;
import xyz.kyngs.librelogin.common.SLF4JLogger;
import xyz.kyngs.librelogin.common.ShutdownException;
import xyz.kyngs.librelogin.common.image.AuthenticImageProjector;
import xyz.kyngs.librelogin.common.util.CancellableTask;
import xyz.kyngs.librelogin.lib.adventure.audience.Audience;
import xyz.kyngs.librelogin.paper.protocol.PacketListener;

/**
 * Paper platform implementation of {@link AuthenticLibreLogin}.
 *
 * <p>Coordinates the PacketEvents login listener (PacketEvents itself is a required external plugin
 * that owns its own lifecycle), Bukkit/Paper event listeners, player authorization lifecycle, and
 * scheduler integrations.
 */
public class PaperLibreLogin extends AuthenticLibreLogin<Player, World> {

    private final PaperBootstrap bootstrap;
    private PaperListeners listeners;
    private PacketListenerCommon packetListener;
    private volatile boolean started;

    /**
     * Constructs a new {@link PaperLibreLogin} instance.
     *
     * @param bootstrap the plugin bootstrap holder
     */
    public PaperLibreLogin(PaperBootstrap bootstrap) {
        this.bootstrap = bootstrap;
        this.started = false;
    }

    /**
     * Retrieves the bootstrap plugin instance.
     *
     * @return the {@link PaperBootstrap} instance
     */
    public PaperBootstrap getBootstrap() {
        return bootstrap;
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return bootstrap.getResource(name);
    }

    @Override
    public File getDataFolder() {
        return bootstrap.getDataFolder();
    }

    @Override
    public String getVersion() {
        return PaperUtil.getPluginVersion(bootstrap);
    }

    @Override
    public boolean isPresent(UUID uuid) {
        return Bukkit.getPlayer(uuid) != null;
    }

    @Override
    public boolean multiProxyEnabled() {
        return false;
    }

    @Override
    public Player getPlayerForUUID(UUID uuid) {
        return Bukkit.getPlayer(uuid);
    }

    @Override
    protected PaperPlatformHandle providePlatformHandle() {
        return new PaperPlatformHandle(this);
    }

    @Override
    protected Logger provideLogger() {
        try {
            return new SLF4JLogger(bootstrap.getSLF4JLogger(), () -> getConfiguration().get(DEBUG));
        } catch (LinkageError e) {
            // Plugin#getSLF4JLogger was added in Paper 1.17.
            return new JULLogger(bootstrap.getLogger(), () -> getConfiguration().get(DEBUG));
        }
    }

    @Override
    public CommandManager<?, ?, ?, ?, ?, ?> provideManager() {
        // ACF's Brigadier support is not enabled: on Paper up to 1.20.x it turns String
        // parameters into Brigadier "word" arguments, which reject characters such as "!", "#"
        // or "@" and make /register and /login fail with "Unknown command".
        return new PaperCommandManager(bootstrap);
    }

    @Override
    public boolean mainThread() {
        return Bukkit.isPrimaryThread() && started;
    }

    @Override
    public Player getPlayerFromIssuer(CommandIssuer issuer) {
        var bukkitIssuer = (BukkitCommandIssuer) issuer;

        return bukkitIssuer.getPlayer();
    }

    @Override
    protected void disable() {
        try {
            // Players are removed only after plugins are disabled: save their data while the
            // database is still open.
            saveLastServersNow(Bukkit.getOnlinePlayers());
        } catch (Throwable t) {
            getLogger().error("Failed to save player data on shutdown", t);
        }
        try {
            org.bukkit.event.HandlerList.unregisterAll(bootstrap);
        } catch (Throwable ignored) {
        }
        try {
            Bukkit.getScheduler().cancelTasks(bootstrap);
        } catch (Throwable ignored) {
        }
        // PacketEvents belongs to its own plugin: only remove our listener, never terminate it.
        var registered = packetListener;
        packetListener = null;
        if (registered != null) {
            try {
                PacketEvents.getAPI().getEventManager().unregisterListener(registered);
            } catch (Throwable ignored) {
            }
        }
        started = false;

        super.disable();
    }

    @Override
    protected void enable() {
        logger = provideLogger();

        if (Bukkit.getOnlineMode()) {
            getLogger()
                    .error(
                            "The backend server is running with 'online-mode=true'. LibreLogin Next"
                                    + " manages authentication dynamically and requires"
                                    + " 'online-mode=false' in server.properties.");
            bootstrap.disable();
            return;
        }

        boolean isBehindProxy = PaperUtil.isProxyEnabled();

        if (isBehindProxy) {
            getLogger()
                    .error(
                            "This backend server is operating behind a proxy network"
                                    + " (BungeeCord/Velocity forwarding enabled).");
            getLogger()
                    .error(
                            "LibreLogin Next must be installed directly on your proxy (Velocity)"
                                    + " instead of backend game servers.");
            bootstrap.disable();
            return;
        }

        try {
            super.enable();
        } catch (ShutdownException e) {
            return;
        }

        var provider = getEventProvider();

        provider.subscribe(
                provider.getTypes().authenticated,
                event -> {
                    var player = event.getPlayer();
                    if (player == null) {
                        return;
                    }
                    if (Bukkit.isPrimaryThread()) {
                        PaperUtil.setInvisible(player, false);
                    } else {
                        Bukkit.getScheduler()
                                .runTask(bootstrap, () -> PaperUtil.setInvisible(player, false));
                    }
                });

        listeners = new PaperListeners(this);

        listeners.registerListeners();
        Bukkit.getPluginManager().registerEvents(new Blockers(this), bootstrap);
        try {
            Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
            Bukkit.getPluginManager().registerEvents(new ModernPaperChatBlocker(this), bootstrap);
        } catch (ClassNotFoundException e) {
            Bukkit.getPluginManager().registerEvents(new LegacyPaperChatBlocker(this), bootstrap);
        }
        packetListener =
                PacketEvents.getAPI()
                        .getEventManager()
                        .registerListener(new PacketListener(listeners));

        started = true;
    }

    @Override
    public void authorize(Player player, User user, Audience audience) {
        try {
            var location = listeners.getSpawnLocationCache().getIfPresent(player.getUniqueId());

            if (location == null) {
                var world = getServerHandler().chooseLobbyServer(user, player, true, false);

                if (world == null) {
                    getPlatformHandle().kick(player, getMessages().getMessage("kick.no-lobby"));
                    return;
                }

                location = world.getSpawnLocation();
            } else {
                listeners.getSpawnLocationCache().invalidate(player.getUniqueId());
            }

            var finalLocation = location;
            try {
                var getSchedulerMethod = player.getClass().getMethod("getScheduler");
                var entityScheduler = getSchedulerMethod.invoke(player);
                var runMethod =
                        entityScheduler
                                .getClass()
                                .getMethod(
                                        "run",
                                        org.bukkit.plugin.Plugin.class,
                                        java.util.function.Consumer.class,
                                        Runnable.class);
                runMethod.invoke(
                        entityScheduler,
                        bootstrap,
                        (java.util.function.Consumer<?>) o -> player.teleportAsync(finalLocation),
                        null);
            } catch (Throwable ignored) {
                if (Bukkit.isPrimaryThread()) {
                    player.teleportAsync(finalLocation);
                } else {
                    Bukkit.getScheduler()
                            .runTask(bootstrap, () -> player.teleportAsync(finalLocation));
                }
            }

        } catch (EventCancelledException ignored) {
        }
    }

    @Override
    public CancellableTask delay(Runnable runnable, long delayInMillis) {
        var task =
                Bukkit.getScheduler()
                        .runTaskLaterAsynchronously(bootstrap, runnable, delayInMillis / 50);
        return task::cancel;
    }

    @Override
    public CancellableTask repeat(Runnable runnable, long delayInMillis, long repeatInMillis) {
        var task =
                Bukkit.getScheduler()
                        .runTaskTimerAsynchronously(
                                bootstrap, runnable, delayInMillis / 50, repeatInMillis / 50);
        return task::cancel;
    }

    @Override
    public boolean pluginPresent(String pluginName) {
        return Bukkit.getPluginManager().isPluginEnabled(pluginName);
    }

    @Override
    protected AuthenticImageProjector<Player, World> provideImageProjector() {
        return null;
    }

    @Override
    public void shutdownProxy(int code) {
        bootstrap.disable();
        bootstrap.getServer().shutdown();
        throw new ShutdownException();
    }

    @Override
    public Audience getAudienceFromIssuer(CommandIssuer issuer) {
        return new PaperAudience(((BukkitCommandIssuer) issuer).getIssuer());
    }
}
