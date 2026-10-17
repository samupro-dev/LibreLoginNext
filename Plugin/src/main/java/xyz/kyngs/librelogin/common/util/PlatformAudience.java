/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.util;

import xyz.kyngs.librelogin.lib.adventure.audience.Audience;

/**
 * An {@link Audience} that delivers LibreLogin's relocated components to a platform object (a
 * player, console or command source).
 */
public interface PlatformAudience extends Audience {

    /**
     * Returns the wrapped platform object.
     *
     * @return the platform sender, player or command source
     */
    Object target();
}
