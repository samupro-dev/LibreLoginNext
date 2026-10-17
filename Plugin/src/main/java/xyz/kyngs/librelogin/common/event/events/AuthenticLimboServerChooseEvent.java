/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.event.events;

import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.LibreLoginPlugin;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.event.events.LimboServerChooseEvent;
import xyz.kyngs.librelogin.common.event.AuthenticServerChooseEvent;

/**
 * Event fired when determining the destination Limbo server for an unauthenticated player.
 *
 * @param <P> the platform-specific player type
 * @param <S> the platform-specific server type
 */
public class AuthenticLimboServerChooseEvent<P, S> extends AuthenticServerChooseEvent<P, S>
        implements LimboServerChooseEvent<P, S> {

    /**
     * Constructs a new Limbo server selection event.
     *
     * @param user the database user, if available
     * @param player the platform player instance
     * @param plugin the LibreLogin plugin instance
     */
    public AuthenticLimboServerChooseEvent(
            @Nullable User user, P player, LibreLoginPlugin<P, S> plugin) {
        super(user, player, plugin);
    }
}
