/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.event.events;

import xyz.kyngs.librelogin.api.event.ServerChooseEvent;

/**
 * Fired when determining the limbo destination server to route an unauthenticated player to.
 *
 * @param <P> the platform-specific player type
 * @param <S> the platform-specific server type
 * @see ServerChooseEvent#setServer
 */
public interface LimboServerChooseEvent<P, S> extends ServerChooseEvent<P, S> {}
