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
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Private;
import co.aikar.commands.annotation.Single;
import co.aikar.commands.annotation.Syntax;
import java.util.concurrent.CompletionStage;
import xyz.kyngs.librelogin.api.event.events.AuthenticatedEvent;
import xyz.kyngs.librelogin.api.event.events.WrongPasswordEvent.AuthenticationSource;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.command.InvalidCommandArgument;
import xyz.kyngs.librelogin.common.crypto.VerifyOnlyCryptoProvider;
import xyz.kyngs.librelogin.common.event.events.AuthenticWrongPasswordEvent;
import xyz.kyngs.librelogin.lib.adventure.audience.Audience;

/**
 * Command handling player login requests, password verification, and 2FA/TOTP validation.
 *
 * @param <P> the platform-specific player type
 */
@Private
@CommandAlias("login|l")
@CommandPermission("librelogin.command.login")
public class LoginCommand<P> extends AuthorizationCommand<P> {

    /**
     * Constructs a new login command handler.
     *
     * @param plugin the plugin instance
     */
    public LoginCommand(AuthenticLibreLogin<P, ?> plugin) {
        super(plugin);
    }

    /**
     * Handles player login authentication.
     *
     * @param sender the audience attempting to log in
     * @param password the plaintext password entered
     * @param code optional TOTP 2FA code
     * @return a completion stage representing execution
     */
    @Default
    @Syntax("{@@syntax.login}")
    @CommandCompletion("%autocomplete.login")
    public CompletionStage<Void> onLogin(
            Audience sender, @Single String password, @Optional String code) {
        return runAsync(
                () -> {
                    var player = getPlayer(sender);
                    checkUnauthorized(player);
                    var user = getUser(player);
                    if (!user.isRegistered()) {
                        throw new InvalidCommandArgument(getMessage("error.not-registered"));
                    }

                    var locked =
                            plugin.getLoginTryListener().remainingLockoutSeconds(user.getUuid());
                    if (locked > 0) {
                        throw new InvalidCommandArgument(
                                getMessage(
                                        "error.login-locked", "%seconds%", String.valueOf(locked)));
                    }

                    if (!getMessages().isEmpty("info.logging-in")) {
                        sender.sendMessage(getMessage("info.logging-in"));
                    }

                    var hashed = user.getHashedPassword();
                    var crypto = getCrypto(hashed);

                    if (crypto == null) {
                        throw new InvalidCommandArgument(getMessage("error.password-corrupted"));
                    }

                    if (!crypto.matches(password, hashed)) {
                        plugin.getEventProvider()
                                .unsafeFire(
                                        plugin.getEventTypes().wrongPassword,
                                        new AuthenticWrongPasswordEvent<>(
                                                user, player, plugin, AuthenticationSource.LOGIN));
                        throw new InvalidCommandArgument(getMessage("error.password-wrong"));
                    }

                    var secret = user.getSecret();

                    if (secret != null) {
                        var totp = plugin.getTOTPProvider();

                        if (totp == null) {
                            // Never fall back to password-only for an account that enabled 2FA.
                            plugin.getLogger()
                                    .error(
                                            "User "
                                                    + user.getLastNickname()
                                                    + " has 2FA enabled but TOTP is unavailable"
                                                    + " (totp.enabled=false?); refusing login.");
                            throw new InvalidCommandArgument(getMessage("totp.unavailable"));
                        } else {
                            if (code == null) {
                                throw new InvalidCommandArgument(getMessage("totp.not-provided"));
                            }

                            int parsedCode;

                            try {
                                parsedCode = Integer.parseInt(code.trim().replace(" ", ""));
                            } catch (NumberFormatException e) {
                                throw new InvalidCommandArgument(getMessage("totp.wrong"));
                            }

                            if (!totp.verify(parsedCode, secret)) {
                                plugin.getEventProvider()
                                        .unsafeFire(
                                                plugin.getEventTypes().wrongPassword,
                                                new AuthenticWrongPasswordEvent<>(
                                                        user,
                                                        player,
                                                        plugin,
                                                        AuthenticationSource.TOTP));
                                throw new InvalidCommandArgument(getMessage("totp.wrong"));
                            }
                        }
                    }

                    if (crypto instanceof VerifyOnlyCryptoProvider) {
                        // Imported legacy hash: upgrade it; authorize() persists the user.
                        var upgraded = plugin.getDefaultCryptoProvider().createHash(password);
                        if (upgraded != null) {
                            user.setHashedPassword(upgraded);
                        }
                    }

                    if (!getMessages().isEmpty("info.logged-in")) {
                        sender.sendMessage(getMessage("info.logged-in"));
                    }
                    getAuthorizationProvider()
                            .authorize(user, player, AuthenticatedEvent.AuthenticationReason.LOGIN);
                });
    }
}
