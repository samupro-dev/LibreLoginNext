/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.velocity;

import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.DEBUG;

import co.aikar.commands.CommandIssuer;
import co.aikar.commands.CommandManager;
import co.aikar.commands.VelocityCommandIssuer;
import co.aikar.commands.VelocityCommandManager;
import com.google.inject.Inject;
import com.velocitypowered.api.network.ProtocolVersion;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import xyz.kyngs.librelogin.api.Logger;
import xyz.kyngs.librelogin.api.PlatformHandle;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.event.exception.EventCancelledException;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.SLF4JLogger;
import xyz.kyngs.librelogin.common.ShutdownException;
import xyz.kyngs.librelogin.common.image.AuthenticImageProjector;
import xyz.kyngs.librelogin.common.image.protocolize.ProtocolizeImageProjector;
import xyz.kyngs.librelogin.common.util.CancellableTask;
import xyz.kyngs.librelogin.common.util.GeneralUtil;
import xyz.kyngs.librelogin.lib.adventure.audience.Audience;

/** Velocity platform implementation of the LibreLogin authentication system. */
public class VelocityLibreLogin extends AuthenticLibreLogin<Player, RegisteredServer> {

    static {
        System.setProperty("auth.forceSecureProfiles", "false");
    }

    private final VelocityBootstrap bootstrap;

    @Inject private org.slf4j.Logger logger;

    @Inject @DataDirectory private Path dataDir;

    @Inject private ProxyServer server;

    @Inject private PluginDescription description;

    /**
     * Constructs the Velocity LibreLogin core implementation.
     *
     * @param bootstrap the Velocity bootstrap plugin instance
     */
    public VelocityLibreLogin(VelocityBootstrap bootstrap) {
        this.bootstrap = bootstrap;
    }

    @Override
    protected void enable() {
        super.enable();
        getLogger().info("LibreLogin Next version " + getVersion() + " enabled!");
    }

    @Override
    protected void disable() {
        super.disable();
    }

    /**
     * Returns the Velocity proxy server instance.
     *
     * @return the proxy server
     */
    public ProxyServer getServer() {
        return server;
    }

    @Override
    protected PlatformHandle<Player, RegisteredServer> providePlatformHandle() {
        return new VelocityPlatformHandle(this);
    }

    @Override
    protected Logger provideLogger() {
        return new SLF4JLogger(logger, () -> getConfiguration().get(DEBUG));
    }

    @Override
    public CommandManager<?, ?, ?, ?, ?, ?> provideManager() {
        return new VelocityCommandManager(server, bootstrap);
    }

    @Override
    public Player getPlayerFromIssuer(CommandIssuer issuer) {
        return ((VelocityCommandIssuer) issuer).getPlayer();
    }

    @Override
    public void authorize(Player player, User user, Audience audience) {
        try {
            var lobby = getServerHandler().chooseLobbyServer(user, player, true, false);
            if (lobby == null) {
                player.disconnect(
                        VelocityAudience.toNative(getMessages().getMessage("kick.no-lobby")));
                return;
            }
            player.createConnectionRequest(lobby)
                    .connect()
                    .whenComplete(
                            (result, throwable) -> {
                                if (throwable != null || result == null || !result.isSuccessful()) {
                                    // The player left while being moved: nothing to report.
                                    if (!player.isActive()) {
                                        return;
                                    }
                                    if (throwable != null) {
                                        getLogger()
                                                .warn(
                                                        "Failed to connect player {} to lobby {}:"
                                                                + " {}",
                                                        player.getUsername(),
                                                        lobby.getServerInfo().getName(),
                                                        GeneralUtil.getFurthestCause(throwable)
                                                                .getMessage());
                                    }
                                    var reason =
                                            result != null
                                                    ? result.getReasonComponent()
                                                            .orElseGet(this::noLobbyReason)
                                                    : noLobbyReason();
                                    player.disconnect(reason);
                                    return;
                                }
                                if (player.getCurrentServer().isEmpty()) {
                                    return;
                                }
                                if (player.getCurrentServer()
                                        .get()
                                        .getServerInfo()
                                        .getName()
                                        .equals(
                                                result.getAttemptedConnection()
                                                        .getServerInfo()
                                                        .getName())) {
                                    return;
                                }
                            });
        } catch (EventCancelledException ignored) {
        }
    }

    private net.kyori.adventure.text.Component noLobbyReason() {
        return VelocityAudience.toNative(getMessages().getMessage("kick.no-lobby"));
    }

    @Override
    public CancellableTask delay(Runnable runnable, long delayInMillis) {
        var task =
                server.getScheduler()
                        .buildTask(bootstrap, runnable)
                        .delay(delayInMillis, TimeUnit.MILLISECONDS)
                        .schedule();
        return task::cancel;
    }

    @Override
    public CancellableTask repeat(Runnable runnable, long delayInMillis, long repeatInMillis) {
        var task =
                server.getScheduler()
                        .buildTask(bootstrap, runnable)
                        .delay(delayInMillis, TimeUnit.MILLISECONDS)
                        .repeat(repeatInMillis, TimeUnit.MILLISECONDS)
                        .schedule();

        return task::cancel;
    }

    @Override
    public boolean pluginPresent(String pluginName) {
        return server.getPluginManager().getPlugin(pluginName).isPresent();
    }

    @Override
    protected AuthenticImageProjector<Player, RegisteredServer> provideImageProjector() {
        if (pluginPresent("protocolize")) {
            var projector = new ProtocolizeImageProjector<>(this);
            var maxProtocol = ProtocolVersion.MAXIMUM_VERSION.getProtocol();

            if (maxProtocol == 760) {
                try {
                    var split = server.getVersion().getVersion().split("-");
                    var build = Integer.parseInt(split[split.length - 1].replace("b", ""));

                    if (build < 172) {
                        logger.warn(
                                "Detected protocolize, but in order for the integration to work"
                                        + " properly, you must be running Velocity build 172 or"
                                        + " newer!");
                        return null;
                    }
                } catch (Exception ignored) {
                }
            }

            if (!projector.compatible()) {
                getLogger()
                        .warn(
                                "Protocolize is installed but reports an incompatible version;"
                                        + " in-game QR map rendering for 2FA will be disabled.");
                return null;
            }
            getLogger().info("Protocolize integration initialized; 2FA map projection enabled.");
            return projector;
        } else {
            getLogger()
                    .info(
                            "Protocolize plugin is not installed; in-game QR map rendering for 2FA"
                                    + " will be unavailable.");
            return null;
        }
    }

    @Override
    public String getVersion() {
        return description.getVersion().orElseThrow();
    }

    @Override
    public Player getPlayerForUUID(UUID uuid) {
        return server.getPlayer(uuid).orElse(null);
    }

    @Override
    public Audience getAudienceFromIssuer(CommandIssuer issuer) {
        return new VelocityAudience(((VelocityCommandIssuer) issuer).getIssuer());
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        return getClass().getClassLoader().getResourceAsStream(name);
    }

    @Override
    public File getDataFolder() {
        return dataDir.toFile();
    }

    @Override
    public void shutdownProxy(int code) {
        if (code != 0) {
            // Messages may not be loaded yet (the failure can be in config.conf or messages.conf).
            var messages = getMessages();
            var reason = messages == null ? null : messages.getMessage("kick.proxy-shutdown");
            server.shutdown(
                    reason != null
                            ? VelocityAudience.toNative(reason)
                            : net.kyori.adventure.text.Component.text(
                                    "LibreLogin Next could not start (see the proxy console): proxy"
                                            + " shutting down."));
        } else {
            server.shutdown();
        }
        // Stop enable() here; the bootstrap keeps every login denied until the proxy has exited.
        throw new ShutdownException(
                "LibreLogin Next requested a proxy shutdown (code " + code + ")");
    }

    @Override
    public boolean mainThread() {
        // Velocity has no main thread; the threads that must never block are Netty's event loops
        // ("Netty epoll Worker #1", "Netty NIO Boss #0", ...). Event handlers and LibreLogin's own
        // workers run on ordinary threads and may block.
        var name = Thread.currentThread().getName();
        return name.startsWith("Netty ") && (name.contains("Worker") || name.contains("Boss"));
    }
}
