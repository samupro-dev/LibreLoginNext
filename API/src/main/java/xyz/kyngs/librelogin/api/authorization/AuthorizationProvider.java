/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.authorization;

import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.event.events.AuthenticatedEvent;

/**
 * Manages player authentication states, authorization lifecycle, and two-factor verification.
 *
 * @param <P> the platform-specific player type
 */
public interface AuthorizationProvider<P> {

    /**
     * Checks whether the player is currently authenticated and authorized.
     *
     * @param player the player to check
     * @return true if the player has successfully completed authentication, false otherwise
     */
    boolean isAuthorized(P player);

    /**
     * Checks whether the player is currently in the process of confirming two-factor
     * authentication.
     *
     * @param player the player to check
     * @return true if awaiting 2FA confirmation, false otherwise
     */
    boolean isAwaiting2FA(P player);

    /**
     * Authorizes the player and marks them as authenticated.
     *
     * @param user the user database profile
     * @param player the platform player instance
     * @param reason the authentication reason triggering this authorization
     */
    void authorize(User user, P player, AuthenticatedEvent.AuthenticationReason reason);

    /**
     * Completes and confirms two-factor authentication setup for a player.
     *
     * @param player the player confirming 2FA
     * @param code the verification code supplied by the player
     * @param user the user profile
     * @return true if the verification code is valid and 2FA was confirmed, false otherwise
     */
    boolean confirmTwoFactorAuth(P player, Integer code, User user);
}
