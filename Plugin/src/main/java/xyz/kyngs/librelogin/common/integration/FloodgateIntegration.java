/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.integration;

import java.util.UUID;
import org.geysermc.floodgate.api.FloodgateApi;
import org.geysermc.floodgate.api.player.FloodgatePlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Integration wrapper for GeyserMC's Floodgate API, providing bedrock player detection and player
 * profile resolution.
 */
public class FloodgateIntegration {

    private final FloodgateApi api;

    /**
     * Constructs a new Floodgate integration handle using the singleton {@link FloodgateApi}
     * instance.
     */
    public FloodgateIntegration() {
        api = FloodgateApi.getInstance();
    }

    /**
     * Checks if the specified UUID corresponds to an authenticated Floodgate (Bedrock) player.
     *
     * @param uuid the unique identifier to check
     * @return true if the UUID belongs to a Floodgate player, false otherwise
     */
    public boolean isFloodgateId(UUID uuid) {
        return api.isFloodgatePlayer(uuid);
    }

    /**
     * Resolves an online {@link FloodgatePlayer} instance by their case-corrected username.
     *
     * @param username the target username
     * @return the matching {@link FloodgatePlayer}, or null if not found
     */
    @Nullable
    public FloodgatePlayer getPlayer(String username) {
        for (FloodgatePlayer floodgatePlayer : api.getPlayers()) {
            if (floodgatePlayer.getCorrectUsername().equals(username)) {
                return floodgatePlayer;
            }
        }

        return null;
    }
}
