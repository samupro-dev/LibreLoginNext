/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.premium;

import java.util.UUID;

/**
 * Encapsulates Mojang premium account profile data.
 *
 * @param uuid the official Mojang UUID of the player
 * @param name the official capitalized username of the player
 * @param reliable whether the username exact casing is guaranteed to match the queried player
 */
public record PremiumUser(UUID uuid, String name, boolean reliable) {}
