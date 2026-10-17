/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.event.events;

import xyz.kyngs.librelogin.api.event.PlayerBasedEvent;

/**
 * Fired after a player has successfully authenticated, whether manually or automatically.
 *
 * @param <P> the platform-specific player type
 * @param <S> the platform-specific server type
 */
public interface AuthenticatedEvent<P, S> extends PlayerBasedEvent<P, S> {

    /**
     * Gets the reason or pathway through which the player authenticated.
     *
     * @return the authentication reason
     */
    AuthenticationReason getReason();

    /** Enumerates the possible causes for successful player authentication. */
    enum AuthenticationReason {
        /**
         * The player authenticated manually using the login command or was logged in by an
         * administrator.
         */
        LOGIN,
        /** The player completed account registration. */
        REGISTER,
        /** The player was automatically authenticated via Mojang premium verification. */
        PREMIUM,
        /** The player was authenticated automatically via an existing active session. */
        SESSION
    }
}
