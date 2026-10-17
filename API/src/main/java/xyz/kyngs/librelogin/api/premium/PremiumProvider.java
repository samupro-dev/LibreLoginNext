/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.premium;

import java.util.UUID;

/** Service provider interface for querying official Mojang premium account metadata. */
public interface PremiumProvider {

    /**
     * Looks up premium account details by Minecraft player username.
     *
     * @param name the username to look up
     * @return the {@link PremiumUser} details, or null if non-premium/not found
     * @throws PremiumException if the lookup request fails due to rate limits or API errors
     */
    PremiumUser getUserForName(String name) throws PremiumException;

    /**
     * Looks up premium account details by official Mojang player UUID.
     *
     * @param uuid the Mojang UUID to look up
     * @return the {@link PremiumUser} details, or null if not found
     * @throws PremiumException if the lookup request fails due to rate limits or API errors
     */
    PremiumUser getUserForUUID(UUID uuid) throws PremiumException;
}
