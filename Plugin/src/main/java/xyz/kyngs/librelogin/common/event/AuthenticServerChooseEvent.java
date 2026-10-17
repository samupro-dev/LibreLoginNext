/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.event;

import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.LibreLoginPlugin;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.event.ServerChooseEvent;

/**
 * Base implementation for server selection events when routing players.
 *
 * @param <P> the platform-specific player type
 * @param <S> the platform-specific server type
 */
public class AuthenticServerChooseEvent<P, S> extends AuthenticPlayerBasedEvent<P, S>
        implements ServerChooseEvent<P, S> {

    private S server;

    /**
     * Constructs a new server choose event.
     *
     * @param user the database user, if loaded
     * @param player the platform player instance
     * @param plugin the LibreLogin plugin instance
     */
    public AuthenticServerChooseEvent(
            @Nullable User user, P player, LibreLoginPlugin<P, S> plugin) {
        super(user, player, plugin);
    }

    @Nullable
    @Override
    public S getServer() {
        return server;
    }

    @Override
    public void setServer(S server) {
        this.server = server;
    }
}
