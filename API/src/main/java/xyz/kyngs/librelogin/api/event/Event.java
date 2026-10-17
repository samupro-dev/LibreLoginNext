/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.event;

import xyz.kyngs.librelogin.api.LibreLoginPlugin;
import xyz.kyngs.librelogin.api.PlatformHandle;

/**
 * Base interface for all LibreLogin plugin events.
 *
 * @param <P> the platform-specific player type
 * @param <S> the platform-specific server type
 */
public interface Event<P, S> {

    /**
     * Gets the main plugin instance.
     *
     * @return the plugin instance
     */
    LibreLoginPlugin<P, S> getPlugin();

    /**
     * Gets the platform handle abstraction.
     *
     * @return the platform handle
     */
    PlatformHandle<P, S> getPlatformHandle();
}
