/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.event.events;

import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.LibreLoginPlugin;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.event.events.LobbyServerChooseEvent;
import xyz.kyngs.librelogin.common.event.AuthenticServerChooseEvent;

/**
 * Event fired when determining the destination lobby server for a player.
 *
 * @param <P> the platform-specific player type
 * @param <S> the platform-specific server type
 */
public class AuthenticLobbyServerChooseEvent<P, S> extends AuthenticServerChooseEvent<P, S>
        implements LobbyServerChooseEvent<P, S> {

    private final Boolean fallback;
    private boolean cancelled;

    /**
     * Constructs a new lobby server selection event.
     *
     * @param user the database user, if available
     * @param player the platform player instance
     * @param plugin the LibreLogin plugin instance
     * @param fallback whether this selection is being performed as a fallback
     */
    public AuthenticLobbyServerChooseEvent(
            @Nullable User user, P player, LibreLoginPlugin<P, S> plugin, Boolean fallback) {
        super(user, player, plugin);
        this.fallback = fallback;
        this.cancelled = false;
    }

    @Override
    public Boolean isFallback() {
        return fallback;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }
}
