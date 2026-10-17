/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.command.commands.staff;

import org.jetbrains.annotations.NotNull;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.command.Command;
import xyz.kyngs.librelogin.common.command.InvalidCommandArgument;

/**
 * Base command class providing common validation and helper methods for administrative / staff
 * commands.
 *
 * @param <P> the platform-specific player type
 */
public class StaffCommand<P> extends Command<P> {

    /**
     * Constructs a new staff command handler.
     *
     * @param plugin the plugin instance
     */
    public StaffCommand(AuthenticLibreLogin<P, ?> plugin) {
        super(plugin);
    }

    /**
     * Retrieves a database user by nickname or throws a user-facing exception if not found.
     *
     * @param name the username to look up
     * @return the resolved user
     * @throws InvalidCommandArgument if no user with that name exists
     */
    @NotNull
    protected User getUserOtherWiseInform(String name) {
        var user = plugin.getDatabaseProvider().getByName(name);

        if (user == null) {
            throw new InvalidCommandArgument(getMessage("error.unknown-user"));
        }

        return user;
    }

    /**
     * Asserts that the specified user is not currently online on the proxy network.
     *
     * @param user the database user
     * @throws InvalidCommandArgument if the player is online
     */
    protected void requireOffline(User user) {
        if (plugin.isPresent(user.getUuid())) {
            throw new InvalidCommandArgument(getMessage("error.player-online"));
        }
    }

    /**
     * Asserts that the specified user is currently online on this proxy instance.
     *
     * @param user the database user
     * @return the online player handle
     * @throws InvalidCommandArgument if on a multi-proxy setup or if the player is offline
     */
    protected P requireOnline(User user) {
        if (plugin.multiProxyEnabled()) {
            throw new InvalidCommandArgument(getMessage("error.not-available-on-multi-proxy"));
        }
        var player = plugin.getPlayerForUUID(user.getUuid());
        if (player == null) {
            throw new InvalidCommandArgument(getMessage("error.player-offline"));
        }
        return player;
    }

    /**
     * Resolves the player handle if online on this proxy, accounting for multi-proxy networks.
     *
     * @param user the database user
     * @return the player handle, or null if not present
     * @throws InvalidCommandArgument if online across a multi-proxy network
     */
    protected P getPossiblyOnlinePlayerOnThisProxy(User user) {
        if (plugin.multiProxyEnabled()) {
            if (plugin.isPresent(user.getUuid())) {
                throw new InvalidCommandArgument(getMessage("error.player-online"));
            } else {
                return null;
            }
        }

        return plugin.getPlayerForUUID(user.getUuid());
    }

    /**
     * Asserts that the given player is not currently authorized.
     *
     * @param player the player to check
     * @throws InvalidCommandArgument if already authorized
     */
    protected void requireUnAuthorized(P player) {
        if (plugin.getAuthorizationProvider().isAuthorized(player)) {
            throw new InvalidCommandArgument(getMessage("error.player-authorized"));
        }
    }

    /**
     * Asserts that the given user has a password registered.
     *
     * @param user the database user
     * @throws InvalidCommandArgument if not registered
     */
    protected void requireRegistered(User user) {
        if (!user.isRegistered()) {
            throw new InvalidCommandArgument(getMessage("error.player-not-registered"));
        }
    }
}
