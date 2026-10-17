/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.velocity;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.command.CommandExecuteEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerChatEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import xyz.kyngs.librelogin.api.authorization.AuthorizationProvider;
import xyz.kyngs.librelogin.api.configuration.Messages;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.common.config.HoconPluginConfiguration;

/**
 * Event listener responsible for blocking restricted actions (chat, unauthorized commands,
 * unauthorized server switches, and kick handling) on Velocity for unauthorized players or players
 * undergoing two-factor authentication.
 */
public class Blockers {

    private final AuthorizationProvider<Player> authorizationProvider;
    private final HoconPluginConfiguration configuration;
    private final Messages messages;

    /**
     * Constructs a new {@code Blockers} listener.
     *
     * @param authorizationProvider the provider supplying player authorization states
     * @param configuration the plugin configuration
     * @param messages the localized messages provider
     */
    public Blockers(
            AuthorizationProvider<Player> authorizationProvider,
            HoconPluginConfiguration configuration,
            Messages messages) {
        this.authorizationProvider = authorizationProvider;
        this.configuration = configuration;
        this.messages = messages;
    }

    /**
     * Prevents chat messages from being sent if the player is unauthorized or awaiting 2FA.
     *
     * @param event the chat event to intercept
     */
    @SuppressWarnings("deprecation")
    @Subscribe(priority = Short.MAX_VALUE - 1)
    public void onChat(PlayerChatEvent event) {
        if (!authorizationProvider.isAuthorized(event.getPlayer())
                || authorizationProvider.isAwaiting2FA(event.getPlayer())) {
            event.setResult(PlayerChatEvent.ChatResult.denied());
        }
    }

    /**
     * Intercepts command execution to block commands unless they are explicitly allowlisted for
     * unauthorized or 2FA-pending players.
     *
     * @param event the command execute event to intercept
     */
    @Subscribe(priority = Short.MAX_VALUE - 1)
    public void onCommand(CommandExecuteEvent event) {
        if (!(event.getCommandSource() instanceof Player player)) {
            return;
        }
        if (authorizationProvider.isAuthorized(player)
                && !authorizationProvider.isAwaiting2FA(player)) {
            return;
        }

        var raw = event.getCommand().trim();
        if (raw.startsWith("/")) {
            raw = raw.substring(1).trim();
        }
        var command = raw.split(" ")[0].toLowerCase(Locale.ROOT);
        var strippedCommand =
                command.contains(":") ? command.substring(command.indexOf(':') + 1) : command;

        for (String allowed :
                configuration.get(ConfigurationKeys.ALLOWED_COMMANDS_WHILE_UNAUTHORIZED)) {
            var lowerAllowed = allowed.toLowerCase(Locale.ROOT);
            if (command.equals(lowerAllowed) || strippedCommand.equals(lowerAllowed)) {
                return;
            }
        }

        event.setResult(CommandExecuteEvent.CommandResult.denied());
        player.sendMessage(VelocityAudience.toNative(messages.getMessage("error.not-authorized")));
    }

    /**
     * Prevents unauthorized players or players awaiting 2FA from connecting to non-limbo servers.
     *
     * @param event the server pre-connect event to intercept
     */
    @Subscribe(priority = Short.MAX_VALUE - 1)
    public void onServerConnect(ServerPreConnectEvent event) {
        if (!authorizationProvider.isAuthorized(event.getPlayer())
                || authorizationProvider.isAwaiting2FA(event.getPlayer())) {
            if (!configuration
                    .get(ConfigurationKeys.LIMBO)
                    .contains(event.getOriginalServer().getServerInfo().getName())) {
                event.setResult(ServerPreConnectEvent.ServerResult.denied());
            }
        }
    }

    /**
     * Intercepts server kicks for unauthorized players or players awaiting 2FA to ensure proper
     * disconnect handling and fallback messaging.
     *
     * @param event the kicked from server event to intercept
     */
    @Subscribe(priority = Short.MAX_VALUE - 1)
    public void onServerKick(KickedFromServerEvent event) {
        if (!authorizationProvider.isAuthorized(event.getPlayer())
                || authorizationProvider.isAwaiting2FA(event.getPlayer())) {
            var fallback = messages.getMessage("kick.no-limbo");
            var reason =
                    event.getServerKickReason()
                            .orElse(
                                    fallback != null
                                            ? VelocityAudience.toNative(fallback)
                                            : Component.text("Limbo not running"));
            event.setResult(KickedFromServerEvent.DisconnectPlayer.create(reason));
        }
    }
}
