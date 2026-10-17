/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.command.commands.authorization;

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
import xyz.kyngs.librelogin.common.event.events.AuthenticPasswordChangeEvent;
import xyz.kyngs.librelogin.common.event.events.AuthenticWrongPasswordEvent;
import xyz.kyngs.librelogin.lib.adventure.audience.Audience;

/**
 * Command handling in-game player password change requests (/changepassword).
 *
 * @param <P> the platform-specific player type
 */
@Private
@CommandAlias("changepassword|changepass")
@CommandPermission("librelogin.command.changepassword")
public class ChangePasswordCommand<P> extends Command<P> {

    /**
     * Constructs a new password change command handler.
     *
     * @param plugin the plugin instance
     */
    public ChangePasswordCommand(AuthenticLibreLogin<P, ?> plugin) {
        super(plugin);
    }

    /**
     * Handles password change execution for authorized players.
     *
     * @param sender the command issuer audience
     * @param oldPass the current plaintext password
     * @param newPass the desired new plaintext password
     * @return a completion stage for async execution
     */
    @Default
    @Syntax("{@@syntax.change-password}")
    @CommandCompletion("%autocomplete.change-password")
    public CompletionStage<Void> onPasswordChange(
            Audience sender, @Single String oldPass, @Single String newPass) {
        return runAsync(
                () -> {
                    var player = getPlayer(sender);
                    checkAuthorized(player);
                    var user = getUser(player);

                    if (!user.isRegistered()) {
                        throw new InvalidCommandArgument(getMessage("error.no-password"));
                    }

                    var hashed = user.getHashedPassword();
                    var crypto = getCrypto(hashed);

                    if (!crypto.matches(oldPass, hashed)) {
                        plugin.getEventProvider()
                                .unsafeFire(
                                        plugin.getEventTypes().wrongPassword,
                                        new AuthenticWrongPasswordEvent<>(
                                                user,
                                                player,
                                                plugin,
                                                AuthenticationSource.CHANGE_PASSWORD));
                        throw new InvalidCommandArgument(getMessage("error.password-wrong"));
                    }

                    setPassword(sender, user, newPass, "info.editing");

                    getDatabaseProvider().updateUser(user);

                    sender.sendMessage(getMessage("info.edited"));

                    plugin.getEventProvider()
                            .unsafeFire(
                                    plugin.getEventTypes().passwordChange,
                                    new AuthenticPasswordChangeEvent<>(
                                            user, player, plugin, hashed));
                });
    }
}
