/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.event;

import org.jetbrains.annotations.Nullable;

/**
 * Event interface allowing listeners to inspect and override server routing decisions.
 *
 * @param <P> the platform-specific player type
 * @param <S> the platform-specific server type
 */
public interface ServerChooseEvent<P, S> extends PlayerBasedEvent<P, S> {

    /**
     * Gets the chosen destination server.
     *
     * @return the destination server, or null if the default server will be used
     */
    @Nullable
    S getServer();

    /**
     * Sets the destination server to connect the player to.
     *
     * @param server the destination server, or null to revert to the default choice
     */
    void setServer(@Nullable S server);
}
