/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper;

import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

/**
 * Fallback listener handling spawn location selection on legacy Spigot/Paper versions where
 * asynchronous player spawn location events are unavailable.
 */
public class LegacySpawnLocationListener implements Listener {

    private final PaperListeners listeners;

    /**
     * Constructs a new {@link LegacySpawnLocationListener}.
     *
     * @param listeners the parent PaperListeners instance
     */
    public LegacySpawnLocationListener(PaperListeners listeners) {
        this.listeners = listeners;
    }

    @SuppressWarnings({"deprecation", "removal"})
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLegacySpawn(PlayerSpawnLocationEvent event) {
        var player = event.getPlayer();
        var puuid = player.getUniqueId();
        boolean isNew = !player.hasPlayedBefore();
        Location newSpawn = listeners.onChooseSpawnLocation(puuid, event.getSpawnLocation(), isNew);
        if (newSpawn != null) {
            event.setSpawnLocation(newSpawn);
        }
    }
}
