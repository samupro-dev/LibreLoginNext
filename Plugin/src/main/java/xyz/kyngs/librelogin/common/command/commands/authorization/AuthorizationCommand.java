/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.command.commands.authorization;

import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.command.Command;
import xyz.kyngs.librelogin.common.command.InvalidCommandArgument;

/**
 * Base command class for authorization-related actions (login, register, etc.) providing validation
 * that the issuer is not yet authorized.
 *
 * @param <P> the platform-specific player type
 */
public class AuthorizationCommand<P> extends Command<P> {

    /**
     * Constructs a new authorization command handler.
     *
     * @param plugin the plugin instance
     */
    public AuthorizationCommand(AuthenticLibreLogin<P, ?> plugin) {
        super(plugin);
    }

    /**
     * Asserts that the given player is not currently authorized.
     *
     * @param player the player to check
     * @throws InvalidCommandArgument if the player is already authorized
     */
    protected void checkUnauthorized(P player) {
        if (getAuthorizationProvider().isAuthorized(player)) {
            throw new InvalidCommandArgument(getMessage("error.already-authorized"));
        }
    }
}
