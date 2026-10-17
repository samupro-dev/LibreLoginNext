/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.config;

/**
 * Strategy used when generating and assigning permanent unique identifiers (UUIDs) for newly
 * registered players.
 */
public enum NewUUIDCreator {

    /** Generates a completely random Type 4 UUID. */
    RANDOM,

    /**
     * Resolves the official online Mojang UUID if the account exists; otherwise falls back to
     * offline mode UUID.
     */
    MOJANG,

    /**
     * Generates an offline mode Type 3 UUID derived from the player's username (standard offline
     * UUID).
     */
    CRACKED
}
