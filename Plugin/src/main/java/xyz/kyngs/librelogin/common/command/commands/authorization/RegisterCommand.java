/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.command.commands.authorization;

import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.REQUIRE_PASSWORD_CONFIRMATION;

import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Private;
import co.aikar.commands.annotation.Single;
import co.aikar.commands.annotation.Syntax;
import java.util.concurrent.CompletionStage;
import xyz.kyngs.librelogin.api.event.events.AuthenticatedEvent;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.command.InvalidCommandArgument;
import xyz.kyngs.librelogin.lib.adventure.audience.Audience;

/**
 * Command handling initial player registration and password setup.
 *
 * @param <P> the platform-specific player type
 */
@Private
@CommandAlias("register|reg")
@CommandPermission("librelogin.command.register")
public class RegisterCommand<P> extends AuthorizationCommand<P> {

    /**
     * Constructs a new registration command handler.
     *
     * @param plugin the plugin instance
     */
    public RegisterCommand(AuthenticLibreLogin<P, ?> plugin) {
        super(plugin);
    }

    /**
     * Handles user registration execution.
     *
     * @param sender the audience registering
     * @param password the desired password
     * @param passwordRepeat repeat of the password for confirmation
     * @return a completion stage representing execution
     */
    @Default
    @Syntax("{@@syntax.register}")
    @CommandCompletion("%autocomplete.register")
    public CompletionStage<Void> onRegister(
            Audience sender, @Single String password, @Single @Optional String passwordRepeat) {
        return runAsync(
                () -> {
                    var player = getPlayer(sender);
                    checkUnauthorized(player);
                    var user = getUser(player);

                    if (user.isRegistered()) {
                        throw new InvalidCommandArgument(getMessage("error.already-registered"));
                    }

                    boolean requireConfirmation =
                            plugin.getConfiguration().get(REQUIRE_PASSWORD_CONFIRMATION);
                    if (requireConfirmation) {
                        if (passwordRepeat == null || !password.contentEquals(passwordRepeat)) {
                            throw new InvalidCommandArgument(
                                    getMessage("error.password-not-match"));
                        }
                    } else if (passwordRepeat != null && !password.contentEquals(passwordRepeat)) {
                        throw new InvalidCommandArgument(getMessage("error.password-not-match"));
                    }

                    setPassword(sender, user, password, "info.registering");

                    if (!getMessages().isEmpty("info.registered")) {
                        sender.sendMessage(getMessage("info.registered"));
                    }

                    getAuthorizationProvider()
                            .authorize(
                                    user, player, AuthenticatedEvent.AuthenticationReason.REGISTER);
                });
    }
}
