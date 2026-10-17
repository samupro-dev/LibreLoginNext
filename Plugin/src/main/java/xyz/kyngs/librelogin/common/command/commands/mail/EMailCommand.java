/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.command.commands.mail;

import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Private;
import co.aikar.commands.annotation.Single;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import xyz.kyngs.librelogin.api.event.events.WrongPasswordEvent.AuthenticationSource;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.authorization.AuthenticAuthorizationProvider;
import xyz.kyngs.librelogin.common.command.Command;
import xyz.kyngs.librelogin.common.command.InvalidCommandArgument;
import xyz.kyngs.librelogin.common.event.events.AuthenticPasswordChangeEvent;
import xyz.kyngs.librelogin.common.event.events.AuthenticWrongPasswordEvent;
import xyz.kyngs.librelogin.common.mail.AuthenticEMailHandler;
import xyz.kyngs.librelogin.common.util.GeneralUtil;
import xyz.kyngs.librelogin.common.util.RateLimiter;
import xyz.kyngs.librelogin.lib.adventure.audience.Audience;

/**
 * Command handling all player email binding, verification, and recovery operations.
 *
 * @param <P> the platform-specific player type
 */
@Private
@CommandAlias("email")
@CommandPermission("librelogin.command.email")
public class EMailCommand<P> extends Command<P> {

    protected final AuthenticEMailHandler mailHandler;
    private final RateLimiter<UUID> limiter;
    private static final RateLimiter<String> DESTINATION_LIMITER =
            new RateLimiter<>(2, TimeUnit.MINUTES);

    /**
     * Constructs a new email-related command handler.
     *
     * @param plugin the plugin instance
     */
    public EMailCommand(AuthenticLibreLogin<P, ?> plugin) {
        super(plugin);
        mailHandler = plugin.getEmailHandler();
        limiter = new RateLimiter<>(1, TimeUnit.MINUTES);
    }

    /**
     * Initiates email address binding by sending a confirmation code to the target email.
     *
     * @param sender the message audience and command sender
     * @param mail the email address
     * @param password the player's current password
     * @return a completion stage representing execution
     */
    @Subcommand("set")
    @Syntax("{@@syntax.email-set}")
    @CommandCompletion("%autocomplete.email-set")
    public CompletionStage<Void> onSetMail(
            Audience sender, @Single String mail, @Single String password) {
        return runAsync(
                () -> {
                    var player = getPlayer(sender);
                    checkAuthorized(player);
                    if (mailHandler == null) {
                        throw new InvalidCommandArgument(getMessage("error.mail-disabled"));
                    }
                    var user = getUser(player);

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
                                                AuthenticationSource.SET_EMAIL));
                        throw new InvalidCommandArgument(getMessage("error.password-wrong"));
                    }

                    if (!GeneralUtil.isValidEmail(mail)) {
                        throw new InvalidCommandArgument(getMessage("error.invalid-email"));
                    }

                    // Per account and per destination, so the server's SMTP cannot be used to
                    // flood one address from many accounts.
                    if (limiter.tryAndLimit(user.getUuid())
                            || DESTINATION_LIMITER.tryAndLimit(
                                    mail.trim().toLowerCase(Locale.ROOT))) {
                        throw new InvalidCommandArgument(getMessage("error.mail-throttle"));
                    }

                    var token = GeneralUtil.generateAlphanumericText(16);

                    sender.sendMessage(getMessage("info.mail-sending"));

                    try {
                        mailHandler.sendVerificationMail(mail, token, user.getLastNickname());
                        getAuthorizationProvider()
                                .getEmailConfirmCache()
                                .put(
                                        user.getUuid(),
                                        new AuthenticAuthorizationProvider.EmailVerifyData(
                                                mail, token, user.getUuid()));
                    } catch (Exception e) {
                        getLogger()
                                .debug(
                                        "Cannot send verification mail to "
                                                + mail
                                                + " for "
                                                + player,
                                        e);
                        throw new InvalidCommandArgument(getMessage("error.mail-not-sent"));
                    }

                    sender.sendMessage(getMessage("info.verification-mail-sent"));
                });
    }

    /**
     * Verifies the email confirmation token and associates the email with the user.
     *
     * @param sender the message audience and command sender
     * @param token the token entered by the player
     * @return a completion stage representing execution
     */
    @Subcommand("verify")
    @Syntax("{@@syntax.email-verify}")
    @CommandCompletion("%autocomplete.email-verify")
    public CompletionStage<Void> onVerifyMail(Audience sender, @Single String token) {
        return runAsync(
                () -> {
                    var player = getPlayer(sender);
                    checkAuthorized(player);
                    var user = getUser(player);

                    var cached =
                            plugin.getAuthorizationProvider()
                                    .getEmailConfirmCache()
                                    .getIfPresent(user.getUuid());
                    if (cached == null) {
                        throw new InvalidCommandArgument(getMessage("error.no-mail-confirm"));
                    }
                    if (!MessageDigest.isEqual(
                            cached.token().getBytes(StandardCharsets.UTF_8),
                            token.getBytes(StandardCharsets.UTF_8))) {
                        throw new InvalidCommandArgument(getMessage("error.wrong-mail-verify"));
                    }
                    plugin.getAuthorizationProvider()
                            .getEmailConfirmCache()
                            .invalidate(user.getUuid());

                    user.setEmail(cached.email());
                    getDatabaseProvider().updateUser(user);

                    sender.sendMessage(getMessage("info.mail-verified"));
                });
    }

    /**
     * Dispatches a password reset token to the player's configured email address.
     *
     * @param audience the message audience and command sender
     * @return a completion stage representing execution
     */
    @Subcommand("reset")
    @Syntax("{@@syntax.email-reset}")
    @CommandCompletion("%autocomplete.email-reset")
    public CompletionStage<Void> onResetPassword(Audience audience) {
        return runAsync(
                () -> {
                    var player = getPlayer(audience);
                    if (mailHandler == null) {
                        throw new InvalidCommandArgument(getMessage("error.mail-disabled"));
                    }
                    var user = getUser(player);

                    if (user.getEmail() == null) {
                        throw new InvalidCommandArgument(getMessage("error.no-email"));
                    }

                    if (limiter.tryAndLimit(user.getUuid())) {
                        throw new InvalidCommandArgument(getMessage("error.mail-throttle"));
                    }

                    var token = GeneralUtil.generateAlphanumericText(16);

                    audience.sendMessage(getMessage("info.mail-sending"));

                    try {
                        mailHandler.sendPasswordResetMail(
                                user.getEmail(),
                                token,
                                user.getLastNickname(),
                                plugin.getPlatformHandle().getIP(player));
                        getAuthorizationProvider()
                                .getPasswordResetCache()
                                .put(user.getUuid(), token);
                    } catch (Exception e) {
                        getLogger()
                                .debug(
                                        "Cannot send password reset mail to "
                                                + user.getEmail()
                                                + " for "
                                                + player,
                                        e);
                        throw new InvalidCommandArgument(getMessage("error.mail-not-sent"));
                    }

                    audience.sendMessage(getMessage("info.reset-password-mail-sent"));
                });
    }

    /**
     * Confirms the reset token sent via email and sets the new password.
     *
     * @param audience the message audience and command sender
     * @param token the secret token received in email
     * @param password the new password
     * @param passwordRepeat repeat of the new password
     * @return a completion stage representing execution
     */
    @Subcommand("reset confirm")
    @Syntax("{@@syntax.email-reset-confirm}")
    @CommandCompletion("%autocomplete.email-reset-confirm")
    public CompletionStage<Void> onConfirmPassReset(
            Audience audience,
            @Single String token,
            @Single String password,
            @Single String passwordRepeat) {
        return runAsync(
                () -> {
                    var player = getPlayer(audience);
                    var user = getUser(player);

                    var cached =
                            plugin.getAuthorizationProvider()
                                    .getPasswordResetCache()
                                    .getIfPresent(user.getUuid());
                    if (cached == null) {
                        throw new InvalidCommandArgument(getMessage("error.no-password-reset"));
                    }
                    if (!MessageDigest.isEqual(
                            cached.getBytes(StandardCharsets.UTF_8),
                            token.getBytes(StandardCharsets.UTF_8))) {
                        throw new InvalidCommandArgument(getMessage("error.wrong-password-reset"));
                    }
                    if (!password.equals(passwordRepeat)) {
                        throw new InvalidCommandArgument(getMessage("error.password-not-match"));
                    }

                    var old = user.getHashedPassword();
                    setPassword(audience, user, password, "info.resetting-password");

                    plugin.getAuthorizationProvider()
                            .getPasswordResetCache()
                            .invalidate(user.getUuid());
                    getDatabaseProvider().updateUser(user);
                    audience.sendMessage(getMessage("info.password-reset"));

                    plugin.getEventProvider()
                            .unsafeFire(
                                    plugin.getEventTypes().passwordChange,
                                    new AuthenticPasswordChangeEvent<>(user, player, plugin, old));
                });
    }
}
