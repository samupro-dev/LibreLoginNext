/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.velocity;

import com.google.inject.Inject;
import com.google.inject.Injector;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import xyz.kyngs.librelogin.api.LibreLoginPlugin;
import xyz.kyngs.librelogin.api.provider.LibreLoginProvider;
import xyz.kyngs.librelogin.common.ShutdownException;

/**
 * Bootstrap entry point for LibreLogin on the Velocity proxy platform.
 *
 * <p>Handles dependency injection and proxy lifecycle event handling.
 */
@Plugin(
        id = "librelogin",
        name = "LibreLogin Next",
        version = "@version@",
        authors = {"vuxeim", "sapphirecode"},
        dependencies = {
            @Dependency(id = "floodgate", optional = true),
            @Dependency(id = "luckperms", optional = true),
            @Dependency(id = "protocolize", optional = true)
        })
public class VelocityBootstrap implements LibreLoginProvider<Player, RegisteredServer> {

    private final ProxyServer server;
    private final Logger logger;
    private final VelocityLibreLogin libreLogin;

    /**
     * Constructs the Velocity plugin bootstrap instance and initializes dependencies.
     *
     * @param server the Velocity proxy server instance
     * @param injector the Guice dependency injector
     * @param logger the SLF4J logger instance
     * @param container the plugin container descriptor
     */
    @Inject
    public VelocityBootstrap(
            ProxyServer server, Injector injector, Logger logger, PluginContainer container) {
        this.server = server;
        this.logger = logger;
        libreLogin = new VelocityLibreLogin(this);
        injector.injectMembers(libreLogin);
    }

    /**
     * Handles proxy initialization by enabling the core plugin and registering event listeners.
     *
     * @param event the proxy initialize event
     */
    @Subscribe
    @SuppressWarnings("deprecation") // PostOrder overload: present on every Velocity 3.x build
    public void onInitialization(ProxyInitializeEvent event) {
        try {
            libreLogin.enable();
        } catch (Throwable t) {
            // Never leave the proxy accepting players without authentication.
            if (!(t instanceof ShutdownException)) {
                logger.error(
                        "LibreLogin Next failed to start. LOCKDOWN: every login is denied.", t);
            }
            server.getEventManager()
                    .register(
                            this,
                            PreLoginEvent.class,
                            PostOrder.FIRST,
                            lockdown ->
                                    lockdown.setResult(
                                            PreLoginEvent.PreLoginComponentResult.denied(
                                                    Component.text(
                                                            "Authentication is unavailable on this"
                                                                    + " proxy. Please contact an"
                                                                    + " administrator."))));
            return;
        }

        server.getEventManager()
                .register(
                        this,
                        new Blockers(
                                libreLogin.getAuthorizationProvider(),
                                libreLogin.getConfiguration(),
                                libreLogin.getMessages()));
        server.getEventManager().register(this, new VelocityListeners(libreLogin));
    }

    @Override
    public LibreLoginPlugin<Player, RegisteredServer> getLibreLogin() {
        return libreLogin;
    }

    /**
     * Handles proxy shutdown by gracefully disabling the plugin.
     *
     * @param event the proxy shutdown event
     */
    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        server.getEventManager().unregisterListeners(this);
        for (var task : server.getScheduler().tasksByPlugin(this)) {
            task.cancel();
        }
        libreLogin.disable();
    }
}
