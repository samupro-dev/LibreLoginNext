/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.image;

import java.awt.image.BufferedImage;

/**
 * Service interface for rendering and projecting image graphics to players.
 *
 * @param <P> the platform-specific player type
 */
public interface ImageProjector<P> {

    /**
     * Projects a buffered image to the target player.
     *
     * @param image the image to project
     * @param player the player receiving the image
     */
    void project(BufferedImage image, P player);

    /**
     * Determines whether image projection is supported for and acceptable by the given player.
     *
     * @param player the player to check
     * @return true if the player can receive projected images, false otherwise
     */
    boolean canProject(P player);
}
