/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.listener;

import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.lib.adventure.text.Component;

/**
 * Encapsulates the evaluation result of a player's pre-login verification handshake.
 *
 * @param state the pre-login authorization state indicating encryption mode or denial
 * @param message the disconnect reason message component if the login is denied
 * @param user the associated database user entity, or null if unassigned
 */
public record PreLoginResult(
        PreLoginState state, @Nullable Component message, @Nullable User user) {}
