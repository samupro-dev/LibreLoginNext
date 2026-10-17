/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper;

import io.papermc.paper.event.player.AsyncPlayerSpawnLocationEvent;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/** Modern Paper event listener handling asynchronous player spawn location assignment. */
public class ModernSpawnLocationListener implements Listener {

    private final PaperListeners listeners;

    /**
     * Constructs a new {@link ModernSpawnLocationListener}.
     *
     * @param listeners the parent PaperListeners instance
     */
    public ModernSpawnLocationListener(PaperListeners listeners) {
        this.listeners = listeners;
    }

    @SuppressWarnings("UnstableApiUsage")
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onAsyncSpawn(AsyncPlayerSpawnLocationEvent event) {
        var puuid = event.getConnection().getProfile().getId();
        Location newSpawn =
                listeners.onChooseSpawnLocation(
                        puuid, event.getSpawnLocation(), event.isNewPlayer());
        if (newSpawn != null) {
            event.setSpawnLocation(newSpawn);
        }
    }
}
