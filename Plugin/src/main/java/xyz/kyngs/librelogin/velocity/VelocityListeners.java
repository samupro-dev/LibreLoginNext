/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.velocity;

import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.connection.PreLoginEvent;
import com.velocitypowered.api.event.player.GameProfileRequestEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.proxy.InboundConnection;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.NoSuchElementException;
import xyz.kyngs.librelogin.api.event.exception.EventCancelledException;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.common.listener.AuthenticListeners;
import xyz.kyngs.librelogin.common.util.GeneralUtil;

/**
 * Event listeners handling player connections, authentication flow, server routing, and proxy
 * lifecycle events on the Velocity platform.
 */
public class VelocityListeners
        extends AuthenticListeners<VelocityLibreLogin, Player, RegisteredServer> {

    private static final AttributeKey<?> FLOODGATE_ATTR = AttributeKey.valueOf("floodgate-player");
    private static final Field INITIAL_MINECRAFT_CONNECTION;
    private static final Field INITIAL_CONNECTION_DELEGATE;
    private static final Method DELEGATED_CONNECTION_METHOD;
    private static final Method GET_CONNECTION_METHOD;
    private static final Method GET_CHANNEL_METHOD;
    private static final Field CHANNEL;

    static {
        Field initialMcConn = null;
        Field initialConnDelegate = null;
        Method delegatedConnMethod = null;
        Method getConnMethod = null;
        Method getChanMethod = null;
        Field chanField = null;

        try {
            Class<?> initialConnection =
                    Class.forName(
                            "com.velocitypowered.proxy.connection.client.InitialInboundConnection");
            Class<?> minecraftConnection =
                    Class.forName("com.velocitypowered.proxy.connection.MinecraftConnection");

            initialMcConn = GeneralUtil.getFieldByType(initialConnection, minecraftConnection);
            if (initialMcConn != null) {
                initialMcConn.setAccessible(true);
            }

            try {
                getConnMethod = initialConnection.getMethod("getConnection");
                getConnMethod.setAccessible(true);
            } catch (NoSuchMethodException ignored) {
            }

            try {
                Class<?> loginInboundConnection =
                        Class.forName(
                                "com.velocitypowered.proxy.connection.client.LoginInboundConnection");
                try {
                    initialConnDelegate = loginInboundConnection.getDeclaredField("delegate");
                    initialConnDelegate.setAccessible(true);
                } catch (NoSuchFieldException ignored) {
                }
                try {
                    delegatedConnMethod =
                            loginInboundConnection.getDeclaredMethod("delegatedConnection");
                    delegatedConnMethod.setAccessible(true);
                } catch (NoSuchMethodException ignored) {
                }
            } catch (ClassNotFoundException ignored) {
            }

            try {
                getChanMethod = minecraftConnection.getMethod("getChannel");
                getChanMethod.setAccessible(true);
            } catch (NoSuchMethodException ignored) {
            }

            chanField = GeneralUtil.getFieldByType(minecraftConnection, Channel.class);
            if (chanField != null) {
                chanField.setAccessible(true);
            }
        } catch (Exception ignored) {
        }

        INITIAL_MINECRAFT_CONNECTION = initialMcConn;
        INITIAL_CONNECTION_DELEGATE = initialConnDelegate;
        DELEGATED_CONNECTION_METHOD = delegatedConnMethod;
        GET_CONNECTION_METHOD = getConnMethod;
        GET_CHANNEL_METHOD = getChanMethod;
        CHANNEL = chanField;
    }

    /**
     * Constructs a new {@code VelocityListeners} instance.
     *
     * @param plugin the Velocity LibreLogin plugin instance
     */
    public VelocityListeners(VelocityLibreLogin plugin) {
        super(plugin);
    }

    /**
     * Extracts the underlying Netty {@link Channel} from the Velocity {@link InboundConnection}.
     *
     * @param connection the inbound connection
     * @return the extracted Netty channel, or {@code null} if extraction fails
     */
    private Channel extractChannel(InboundConnection connection) {
        try {
            Object mcConn = null;

            if (DELEGATED_CONNECTION_METHOD != null
                    && connection != null
                    && DELEGATED_CONNECTION_METHOD.getDeclaringClass().isInstance(connection)) {
                mcConn = DELEGATED_CONNECTION_METHOD.invoke(connection);
            } else if (INITIAL_CONNECTION_DELEGATE != null
                    && connection != null
                    && INITIAL_CONNECTION_DELEGATE.getDeclaringClass().isInstance(connection)) {
                Object delegate = INITIAL_CONNECTION_DELEGATE.get(connection);
                if (delegate != null) {
                    if (GET_CONNECTION_METHOD != null) {
                        mcConn = GET_CONNECTION_METHOD.invoke(delegate);
                    } else if (INITIAL_MINECRAFT_CONNECTION != null) {
                        mcConn = INITIAL_MINECRAFT_CONNECTION.get(delegate);
                    }
                }
            } else if (connection != null) {
                if (GET_CONNECTION_METHOD != null
                        && GET_CONNECTION_METHOD.getDeclaringClass().isInstance(connection)) {
                    mcConn = GET_CONNECTION_METHOD.invoke(connection);
                } else if (INITIAL_MINECRAFT_CONNECTION != null
                        && INITIAL_MINECRAFT_CONNECTION
                                .getDeclaringClass()
                                .isInstance(connection)) {
                    mcConn = INITIAL_MINECRAFT_CONNECTION.get(connection);
                }
            }

            if (mcConn != null) {
                if (GET_CHANNEL_METHOD != null) {
                    return (Channel) GET_CHANNEL_METHOD.invoke(mcConn);
                }
                if (CHANNEL != null) {
                    return (Channel) CHANNEL.get(mcConn);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Handles post-login logic once the player has successfully connected to the proxy.
     *
     * @param event the post-login event
     */
    @Subscribe(priority = Short.MIN_VALUE + 1)
    public EventTask onPostLogin(PostLoginEvent event) {
        return EventTask.async(() -> onPostLogin(event.getPlayer(), null));
    }

    /**
     * Handles player disconnect events to clear pending states and sessions.
     *
     * @param event the disconnect event
     */
    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        GeneralUtil.runAsync(() -> onPlayerDisconnect(event.getPlayer()));
    }

    /**
     * Intercepts game profile requests to associate existing database UUIDs with players.
     *
     * @param event the game profile request event
     */
    @Subscribe
    public EventTask onProfileRequest(GameProfileRequestEvent event) {
        return EventTask.async(
                () -> {
                    var existing = event.getGameProfile();

                    if (existing != null && plugin.fromFloodgate(existing.getId())) {
                        return;
                    }

                    try {
                        var profile = plugin.getDatabaseProvider().getByName(event.getUsername());
                        if (profile == null) {
                            return;
                        }

                        var gProfile = event.getOriginalProfile();
                        event.setGameProfile(gProfile.withId(profile.getUuid()));
                    } catch (Exception e) {
                        plugin.getLogger()
                                .error(
                                        "Failed to fetch profile from database for "
                                                + event.getUsername(),
                                        e);
                    }
                });
    }

    /**
     * Intercepts pre-login events to perform authorization checks and determine online/offline
     * mode.
     *
     * @param event the pre-login event
     */
    @Subscribe(priority = Short.MIN_VALUE + 1)
    public EventTask onPreLogin(PreLoginEvent event) {
        if (!event.getResult().isAllowed()) {
            return null;
        }

        if (plugin.floodgateEnabled()) {
            try {
                Channel channel = extractChannel(event.getConnection());
                if (channel != null && channel.attr(FLOODGATE_ATTR).get() != null) {
                    return null;
                }
            } catch (Exception e) {
                plugin.getLogger()
                        .warn(
                                "Failed to check Floodgate player state during pre-login for '{}':"
                                        + " {}",
                                event.getUsername(),
                                e.getMessage());
                event.setResult(
                        PreLoginEvent.PreLoginComponentResult.denied(
                                VelocityAudience.toNative(
                                        plugin.getMessages().getMessage("kick.internal-error"))));
                return null;
            }
        }

        return EventTask.async(
                () -> {
                    var result =
                            onPreLogin(
                                    event.getUsername(),
                                    event.getConnection().getRemoteAddress().getAddress());

                    event.setResult(
                            switch (result.state()) {
                                case DENIED -> {
                                    assert result.message() != null;
                                    yield PreLoginEvent.PreLoginComponentResult.denied(
                                            VelocityAudience.toNative(result.message()));
                                }
                                case FORCE_ONLINE ->
                                        PreLoginEvent.PreLoginComponentResult.forceOnlineMode();
                                case FORCE_OFFLINE ->
                                        PreLoginEvent.PreLoginComponentResult.forceOfflineMode();
                            });
                });
    }

    /**
     * Selects the initial server (limbo or lobby) when a player joins the proxy.
     *
     * @param event the initial server selection event
     */
    @Subscribe(priority = Short.MIN_VALUE + 1)
    public EventTask chooseServer(PlayerChooseInitialServerEvent event) {
        return EventTask.async(
                () -> {
                    var server = chooseServer(event.getPlayer(), null, null);

                    if (server.value() == null) {
                        event.getPlayer()
                                .disconnect(
                                        VelocityAudience.toNative(
                                                plugin.getMessages()
                                                        .getMessage(
                                                                "kick.no-"
                                                                        + (server.key()
                                                                                ? "lobby"
                                                                                : "limbo"))));
                        event.setInitialServer(null);
                    } else {
                        event.setInitialServer(server.value());
                    }
                });
    }

    /**
     * Handles kicked from server events to route or disconnect players gracefully.
     *
     * @param event the kicked from server event
     */
    @Subscribe(priority = Short.MAX_VALUE / 2)
    public EventTask onKick(KickedFromServerEvent event) {
        var reason =
                event.getServerKickReason()
                        .map(VelocityAudience::fromNative)
                        .orElseGet(
                                () -> plugin.getMessages().getMessage("info.kick-unknown-reason"));
        var message =
                VelocityAudience.toNative(
                        plugin.getMessages()
                                .getMessage("info.kick")
                                .replaceText(
                                        builder ->
                                                builder.matchLiteral("%reason%")
                                                        .replacement(reason)));
        var player = event.getPlayer();

        if (event.kickedDuringServerConnect()) {
            event.setResult(KickedFromServerEvent.Notify.create(message));
            return null;
        }

        if (!plugin.getConfiguration().get(ConfigurationKeys.FALLBACK)
                || plugin.getServerHandler().getLobbyServers().containsValue(event.getServer())) {
            event.setResult(KickedFromServerEvent.DisconnectPlayer.create(message));
            return null;
        }

        return EventTask.async(
                () -> {
                    try {
                        var server =
                                plugin.getServerHandler()
                                        .chooseLobbyServer(
                                                plugin.getDatabaseProvider()
                                                        .getByUUID(player.getUniqueId()),
                                                player,
                                                false,
                                                true);

                        if (server == null) {
                            throw new NoSuchElementException();
                        }

                        event.setResult(
                                KickedFromServerEvent.RedirectPlayer.create(server, message));
                    } catch (NoSuchElementException | EventCancelledException e) {
                        event.setResult(KickedFromServerEvent.DisconnectPlayer.create(message));
                    }
                });
    }
}
