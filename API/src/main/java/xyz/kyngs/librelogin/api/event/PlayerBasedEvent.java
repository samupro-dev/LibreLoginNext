/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.event;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.lib.adventure.audience.Audience;

/**
 * Base interface for events involving a specific player and their database profile.
 *
 * @param <P> the platform-specific player type
 * @param <S> the platform-specific server type
 */
public interface PlayerBasedEvent<P, S> extends Event<P, S> {

    /**
     * Gets the UUID of the player associated with this event.
     *
     * @return the player UUID
     */
    UUID getUUID();

    /**
     * Gets the underlying platform player instance if locally connected.
     *
     * @return the player instance, or null if the action was performed remotely/offline
     */
    @Nullable
    P getPlayer();

    /**
     * Gets the Adventure {@link Audience} for sending messages, titles, and action bars to the
     * player.
     *
     * @return the player audience
     */
    Audience getAudience();

    /**
     * Gets the database user record associated with the player.
     *
     * @return the database user profile, or null if unlinked
     */
    @Nullable
    User getUser();
}
