/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.image;

import xyz.kyngs.librelogin.api.image.ImageProjector;
import xyz.kyngs.librelogin.common.AuthenticHandler;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;

/**
 * Base abstract class for projecting buffered images (e.g. 2FA QR codes) to in-game player client
 * maps.
 *
 * @param <P> the platform-specific player type
 * @param <S> the platform-specific server type
 */
public abstract class AuthenticImageProjector<P, S> extends AuthenticHandler<P, S>
        implements ImageProjector<P> {

    /**
     * Constructs a new image projector handler.
     *
     * @param plugin the LibreLogin plugin instance
     */
    public AuthenticImageProjector(AuthenticLibreLogin<P, S> plugin) {
        super(plugin);
    }

    /**
     * Enables and initializes the image projector subsystem (e.g. registering packet
     * listeners/modules).
     */
    public abstract void enable();
}
