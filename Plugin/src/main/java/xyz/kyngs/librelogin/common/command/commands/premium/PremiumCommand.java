/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.command.commands.premium;

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
import xyz.kyngs.librelogin.common.command.InvalidCommandArgument;
import xyz.kyngs.librelogin.common.event.events.AuthenticWrongPasswordEvent;
import xyz.kyngs.librelogin.lib.adventure.audience.Audience;

/**
 * Command handling the initiation and confirmation of official Mojang AutoLogin mode (/premium).
 *
 * @param <P> the platform-specific player type
 */
@Private
@CommandAlias("premium")
@CommandPermission("librelogin.command.premium")
public class PremiumCommand<P> extends AbstractPremiumCommand<P> {

    /**
     * Constructs a new premium command handler.
     *
     * @param plugin the plugin instance
     */
    public PremiumCommand(AuthenticLibreLogin<P, ?> plugin) {
        super(plugin);
    }

    /**
     * Initiates the premium enable process by verifying password and storing a confirmation token.
     *
     * @param sender the audience enabling premium status
     * @param password the player's current password
     * @return a completion stage representing execution
     */
    @Default
    @Syntax("{@@syntax.premium}")
    @CommandCompletion("%autocomplete.premium")
    public CompletionStage<Void> onPremium(Audience sender, @Single String password) {
        return runAsync(
                () -> {
                    var player = getPlayer(sender);
                    checkAuthorized(player);
                    var user = getUser(player);
                    checkCracked(user);

                    var hashed = user.getHashedPassword();
                    var crypto = getCrypto(hashed);

                    if (!crypto.matches(password, hashed)) {
                        plugin.getEventProvider()
                                .unsafeFire(
                                        plugin.getEventTypes().wrongPassword,
                                        new AuthenticWrongPasswordEvent<>(
                                                user,
                                                player,
                                                plugin,
                                                AuthenticationSource.PREMIUM_ENABLE));
                        throw new InvalidCommandArgument(getMessage("error.password-wrong"));
                    }

                    plugin.getCommandProvider().registerConfirm(user.getUuid());

                    sender.sendMessage(getMessage("prompt.confirm"));
                });
    }

    /**
     * Confirms and activates premium status for the player.
     *
     * @param sender the audience confirming premium status
     * @return a completion stage representing execution
     */
    @Subcommand("confirm")
    @Syntax("{@@syntax.premium-confirm}")
    public CompletionStage<Void> onPremiumConfirm(Audience sender) {
        return runAsync(
                () -> {
                    var player = getPlayer(sender);
                    checkAuthorized(player);
                    var user = getUser(player);
                    checkCracked(user);

                    plugin.getCommandProvider().onConfirm(player, sender, user);
                });
    }
}
