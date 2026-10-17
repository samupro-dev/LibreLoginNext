/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.command.commands.tfa;

import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Private;
import co.aikar.commands.annotation.Single;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import java.util.concurrent.CompletionStage;
import xyz.kyngs.librelogin.api.event.events.WrongPasswordEvent.AuthenticationSource;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.command.Command;
import xyz.kyngs.librelogin.common.command.InvalidCommandArgument;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.common.event.events.AuthenticWrongPasswordEvent;
import xyz.kyngs.librelogin.lib.adventure.audience.Audience;

/**
 * Command allowing authorized players to initiate and confirm setup for two-factor authentication
 * (TOTP) (/2fa).
 *
 * @param <P> the platform-specific player type
 */
@Private
@CommandAlias("2fa|totp")
@CommandPermission("librelogin.command.2fa")
public class TwoFactorCommand<P> extends Command<P> {

    /**
     * Constructs a new two-factor authentication initiation and confirmation command handler.
     *
     * @param plugin the plugin instance
     */
    public TwoFactorCommand(AuthenticLibreLogin<P, ?> plugin) {
        super(plugin);
    }

    /**
     * Generates a new TOTP secret and projects the QR code onto a map in the player's inventory.
     *
     * @param sender the message audience and command sender
     * @return a completion stage representing execution
     */
    @Default
    public CompletionStage<Void> onTwoFactorAuth(Audience sender) {
        return runAsync(
                () -> {
                    var player = getPlayer(sender);
                    checkAuthorized(player);
                    var user = getUser(player);
                    var auth = plugin.getAuthorizationProvider();

                    if (auth.isAwaiting2FA(player)) {
                        var current = plugin.getImageProjector();
                        throw new InvalidCommandArgument(
                                getMessage(
                                        current != null && current.canProject(player)
                                                ? "totp.show-info"
                                                : "totp.show-info-manual"));
                    }

                    if (user.getSecret() != null) {
                        // Replacing a secret must go through /2fa disable <password> first.
                        throw new InvalidCommandArgument(getMessage("totp.already-enabled"));
                    }

                    var projector = plugin.getImageProjector();
                    boolean canProject = projector != null && projector.canProject(player);

                    // The QR code map exists only on Velocity with Protocolize; elsewhere the
                    // secret is entered manually.
                    if (canProject) {
                        sender.sendMessage(getMessage("totp.generating"));
                    }

                    var data = plugin.getTOTPProvider().generate(user);

                    auth.beginTwoFactorAuth(user, player, data);

                    plugin.cancelOnExit(
                            plugin.delay(
                                    () -> {
                                        boolean projected =
                                                canProject && plugin.getImageProjector() != null;
                                        if (projected) {
                                            try {
                                                plugin.getImageProjector()
                                                        .project(data.qr(), player);
                                            } catch (RuntimeException e) {
                                                // Fall back to the secret in chat instead of
                                                // leaving the player without setup instructions.
                                                projected = false;
                                                plugin.getLogger()
                                                        .warn(
                                                                "Could not show the 2FA QR code"
                                                                    + " map, falling back to the"
                                                                    + " secret key: {}",
                                                                e.getMessage());
                                            }
                                        }
                                        sender.sendMessage(
                                                getMessage(
                                                        projected
                                                                ? "totp.show-info"
                                                                : "totp.show-info-manual"));
                                        sender.sendMessage(
                                                getMessage(
                                                        "totp.secret-key",
                                                        "%code%",
                                                        data.secret()));
                                    },
                                    plugin.getConfiguration().get(ConfigurationKeys.TOTP_DELAY)),
                            player);
                });
    }

    /**
     * Confirms the generated 2FA code and enables TOTP for the player account.
     *
     * @param sender the message audience and command sender
     * @param code the TOTP code entered
     * @return a completion stage representing execution
     */
    @Subcommand("confirm")
    @Syntax("{@@syntax.2fa-confirm}")
    @CommandCompletion("%autocomplete.2fa-confirm")
    public CompletionStage<Void> onTwoFactorConfirm(Audience sender, @Single String code) {
        return runAsync(
                () -> {
                    var player = getPlayer(sender);
                    checkAuthorized(player);
                    var user = getUser(player);
                    var auth = plugin.getAuthorizationProvider();

                    if (!auth.isAwaiting2FA(player)) {
                        throw new InvalidCommandArgument(getMessage("totp.not-awaiting"));
                    }

                    int parsedCode;

                    try {
                        parsedCode = Integer.parseInt(code.trim().replace(" ", ""));
                    } catch (NumberFormatException e) {
                        throw new InvalidCommandArgument(getMessage("totp.wrong"));
                    }

                    if (!auth.confirmTwoFactorAuth(player, parsedCode, user)) {
                        throw new InvalidCommandArgument(getMessage("totp.wrong"));
                    }

                    plugin.getPlatformHandle().kick(player, getMessage("kick.2fa-enabled"));
                });
    }

    /**
     * Disables two-factor authentication for the player account after password verification.
     *
     * @param sender the message audience and command sender
     * @param password the player's account password
     * @return a completion stage representing execution
     */
    @Subcommand("disable")
    @Syntax("{@@syntax.2fa-disable}")
    @CommandCompletion("%autocomplete.2fa-disable")
    public CompletionStage<Void> onTwoFactorDisable(Audience sender, @Single String password) {
        return runAsync(
                () -> {
                    var player = getPlayer(sender);
                    checkAuthorized(player);
                    var user = getUser(player);

                    if (user.getSecret() == null) {
                        throw new InvalidCommandArgument(getMessage("error.2fa-not-enabled"));
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
                                                AuthenticationSource.TFA_DISABLE));
                        throw new InvalidCommandArgument(getMessage("error.password-wrong"));
                    }

                    user.setSecret(null);
                    revokeSessions(user);
                    getDatabaseProvider().updateUser(user);

                    if (!getMessages().isEmpty("info.2fa-disabled")) {
                        sender.sendMessage(getMessage("info.2fa-disabled"));
                    }
                });
    }
}
