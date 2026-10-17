/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.listener;

/** Represents the required authentication mode or disposition determined during pre-login. */
public enum PreLoginState {

    /** Forces online-mode Mojang encryption and session validation for the connecting player. */
    FORCE_ONLINE,

    /** Forces offline-mode connection, bypassing online Mojang authentication. */
    FORCE_OFFLINE,

    /** Denies the connection attempt, rejecting the player during the handshake. */
    DENIED
}
