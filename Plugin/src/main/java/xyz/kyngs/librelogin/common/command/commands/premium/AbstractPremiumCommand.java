/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.command.commands.premium;

import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.command.InvalidCommandArgument;
import xyz.kyngs.librelogin.common.command.commands.authorization.AuthorizationCommand;

/**
 * Abstract base command class for managing premium autologin state transitions and account
 * assertions.
 *
 * @param <P> the platform-specific player type
 */
public abstract class AbstractPremiumCommand<P> extends AuthorizationCommand<P> {

    /**
     * Constructs a new abstract premium command base handler.
     *
     * @param plugin the plugin instance
     */
    public AbstractPremiumCommand(AuthenticLibreLogin<P, ?> plugin) {
        super(plugin);
    }

    /**
     * Asserts that the specified user does not currently have premium autologin enabled.
     *
     * @param user the database user
     * @throws InvalidCommandArgument if autologin is already enabled
     */
    protected void checkCracked(User user) {
        if (user.autoLoginEnabled()) {
            throw new InvalidCommandArgument(getMessage("error.not-cracked"));
        }
    }

    /**
     * Asserts that the specified user currently has premium autologin enabled.
     *
     * @param user the database user
     * @throws InvalidCommandArgument if autologin is not enabled
     */
    protected void checkPremium(User user) {
        if (!user.autoLoginEnabled()) {
            throw new InvalidCommandArgument(getMessage("error.not-premium"));
        }
    }
}
