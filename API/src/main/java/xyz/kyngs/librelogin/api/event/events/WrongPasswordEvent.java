/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.event.events;

import xyz.kyngs.librelogin.api.event.PlayerBasedEvent;

/**
 * Fired when a player enters an incorrect password or invalid TOTP code during authentication or
 * security actions.
 *
 * @param <P> the platform-specific player type
 * @param <S> the platform-specific server type
 */
public interface WrongPasswordEvent<P, S> extends PlayerBasedEvent<P, S> {

    /**
     * Gets the specific authentication or verification action that failed.
     *
     * @return the authentication failure source
     */
    AuthenticationSource getSource();

    /** Enumerates the context sources where an authentication failure occurred. */
    enum AuthenticationSource {
        /** The player entered an incorrect password during login. */
        LOGIN,
        /** The player entered an incorrect TOTP verification code. */
        TOTP,
        /** The player supplied an invalid current password while attempting a password change. */
        CHANGE_PASSWORD,
        /** The player supplied an invalid password while attempting to toggle premium status. */
        PREMIUM_ENABLE,
        /**
         * The player supplied an invalid password while attempting to configure an email address.
         */
        SET_EMAIL,
        /** The player supplied an invalid password while attempting to unregister. */
        UNREGISTER,
        /** The player supplied an invalid password while attempting to disable 2FA. */
        TFA_DISABLE
    }
}
