/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.authorization;

/**
 * Strategy defining how profile collisions are handled when a connecting player's Mojang profile
 * conflicts with an existing database record.
 */
public enum ProfileConflictResolutionStrategy {

    /** Rejects the connection attempt and kicks the player when a profile conflict occurs. */
    BLOCK,

    /** Disregards the conflicting online profile and forces an offline-mode profile session. */
    USE_OFFLINE,

    /** Overwrites the conflicting existing record with the incoming authenticated profile. */
    OVERWRITE
}
