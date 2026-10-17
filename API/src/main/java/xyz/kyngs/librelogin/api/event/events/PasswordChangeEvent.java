/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.event.events;

import xyz.kyngs.librelogin.api.crypto.HashedPassword;
import xyz.kyngs.librelogin.api.event.PlayerBasedEvent;

/**
 * Fired after a player's password has been updated. The player's user profile already contains the
 * updated password at the time this event is fired.
 *
 * @param <P> the platform-specific player type
 * @param <S> the platform-specific server type
 */
public interface PasswordChangeEvent<P, S> extends PlayerBasedEvent<P, S> {

    /**
     * Gets the previous hashed password before the update took place.
     *
     * @return the old hashed password
     */
    HashedPassword getOldPassword();
}
