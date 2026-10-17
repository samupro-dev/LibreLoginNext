/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.integration;

/**
 * Integration interface providing support for virtual Limbo server instances.
 *
 * @param <S> the platform-specific server type
 */
public interface LimboIntegration<S> {

    /**
     * Dynamically creates a virtual Limbo server instance with the given identifier.
     *
     * @param serverName the name identifier of the limbo server
     * @return the created limbo server instance
     */
    S createLimbo(String serverName);
}
