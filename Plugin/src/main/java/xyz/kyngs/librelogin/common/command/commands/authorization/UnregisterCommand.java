/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.command.commands.authorization;

import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.ALLOW_PLAYER_UNREGISTER;

import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Private;
import co.aikar.commands.annotation.Single;
import co.aikar.commands.annotation.Syntax;
import java.util.concurrent.CompletionStage;
import xyz.kyngs.librelogin.api.event.events.WrongPasswordEvent.AuthenticationSource;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.command.Command;
import xyz.kyngs.librelogin.common.command.InvalidCommandArgument;
import xyz.kyngs.librelogin.common.event.events.AuthenticWrongPasswordEvent;
import xyz.kyngs.librelogin.lib.adventure.audience.Audience;

/**
 * Command allowing authenticated players to delete their own account registration (/unregister
 * <password>).
 *
 * @param <P> the platform-specific player type
 */
@Private
@CommandAlias("unregister")
@CommandPermission("librelogin.command.unregister")
public class UnregisterCommand<P> extends Command<P> {

    /**
     * Constructs a new unregister command handler.
     *
     * @param plugin the plugin instance
     */
    public UnregisterCommand(AuthenticLibreLogin<P, ?> plugin) {
        super(plugin);
    }

    /**
     * Handles account unregistration.
     *
     * @param sender the audience executing unregister
     * @param password the player's current password for confirmation
     * @return a completion stage representing execution
     */
    @Default
    @Syntax("{@@syntax.unregister}")
    @CommandCompletion("%autocomplete.unregister")
    public CompletionStage<Void> onUnregister(Audience sender, @Single String password) {
        return runAsync(
                () -> {
                    var player = getPlayer(sender);
                    if (!plugin.getConfiguration().get(ALLOW_PLAYER_UNREGISTER)) {
                        throw new InvalidCommandArgument(getMessage("error.command-disabled"));
                    }

                    checkAuthorized(player);
                    var user = getUser(player);

                    if (!user.isRegistered()) {
                        throw new InvalidCommandArgument(getMessage("error.no-password"));
                    }

                    var hashed = user.getHashedPassword();
                    if (hashed == null) {
                        throw new InvalidCommandArgument(getMessage("error.no-password"));
                    }

                    var crypto = getCrypto(hashed);
                    if (!crypto.matches(password, hashed)) {
                        plugin.getEventProvider()
                                .unsafeFire(
                                        plugin.getEventTypes().wrongPassword,
                                        new AuthenticWrongPasswordEvent<>(
                                                user,
                                                player,
                                                plugin,
                                                AuthenticationSource.UNREGISTER));
                        throw new InvalidCommandArgument(getMessage("error.password-wrong"));
                    }

                    user.setHashedPassword(null);
                    user.setSecret(null);
                    user.setEmail(null);
                    getDatabaseProvider().updateUser(user);

                    plugin.getPlatformHandle().kick(player, getMessage("kick.unregistered"));
                });
    }
}
