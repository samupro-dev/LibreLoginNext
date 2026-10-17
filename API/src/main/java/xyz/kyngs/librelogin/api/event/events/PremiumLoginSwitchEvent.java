/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.event.events;

import xyz.kyngs.librelogin.api.event.PlayerBasedEvent;

/**
 * Fired when a player toggles their premium auto-login setting.
 *
 * <p>This event is dispatched after the local user state has been updated in memory, but before the
 * user record is written to the persistent database.
 *
 * @param <P> the platform-specific player type
 * @param <S> the platform-specific server type
 */
public interface PremiumLoginSwitchEvent<P, S> extends PlayerBasedEvent<P, S> {}
