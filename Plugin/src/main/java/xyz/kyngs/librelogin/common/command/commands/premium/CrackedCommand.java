/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.command.commands.premium;

import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Default;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Private;
import co.aikar.commands.annotation.Single;
import java.util.concurrent.CompletionStage;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.command.InvalidCommandArgument;
import xyz.kyngs.librelogin.common.event.events.AuthenticPremiumLoginSwitchEvent;
import xyz.kyngs.librelogin.lib.adventure.audience.Audience;

/**
 * Command allowing players to disable premium autologin and return to cracked/manual authentication
 * (/cracked).
 *
 * @param <P> the platform-specific player type
 */
@Private
@CommandAlias("cracked")
@CommandPermission("librelogin.command.cracked")
public class CrackedCommand<P> extends AbstractPremiumCommand<P> {

    /**
     * Constructs a new cracked command handler.
     *
     * @param plugin the plugin instance
     */
    public CrackedCommand(AuthenticLibreLogin<P, ?> plugin) {
        super(plugin);
    }

    /**
     * Disables premium autologin for the executing player and disconnects them.
     *
     * @param sender the audience disabling premium status
     * @param password the new password, required only when the account has none
     * @param passwordRepeat the new password repeated
     * @return a completion stage representing execution
     */
    @Default
    public CompletionStage<Void> onCracked(
            Audience sender,
            @Single @Optional String password,
            @Single @Optional String passwordRepeat) {
        return runAsync(
                () -> {
                    var player = getPlayer(sender);
                    checkAuthorized(player);
                    var user = getUser(player);
                    checkPremium(user);
                    if (!user.isRegistered()) {
                        // Without a password the account would become unregistered and claimable
                        // by anyone who joins with this name, so one is set in the same step.
                        if (password == null || passwordRepeat == null) {
                            throw new InvalidCommandArgument(
                                    getMessage("error.cracked-requires-password"));
                        }
                        if (!password.equals(passwordRepeat)) {
                            throw new InvalidCommandArgument(
                                    getMessage("error.password-not-match"));
                        }
                        setPassword(sender, user, password, "info.editing");
                    }

                    sender.sendMessage(getMessage("info.disabling"));

                    user.setPremiumUUID(null);

                    plugin.getEventProvider()
                            .unsafeFire(
                                    plugin.getEventTypes().premiumLoginSwitch,
                                    new AuthenticPremiumLoginSwitchEvent<>(user, player, plugin));

                    getDatabaseProvider().updateUser(user);

                    plugin.getPlatformHandle()
                            .kick(player, getMessage("kick.premium-info-disabled"));
                });
    }
}
