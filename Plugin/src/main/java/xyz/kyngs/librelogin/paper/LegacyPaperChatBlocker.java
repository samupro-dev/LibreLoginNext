/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import xyz.kyngs.librelogin.api.authorization.AuthorizationProvider;

/**
 * Legacy Paper/Spigot chat blocker utilizing {@link AsyncPlayerChatEvent} for Paper 1.13.2 through
 * 1.16.4.
 */
@SuppressWarnings("deprecation")
public class LegacyPaperChatBlocker implements Listener {

    private final AuthorizationProvider<Player> authorizationProvider;

    public LegacyPaperChatBlocker(PaperLibreLogin plugin) {
        this.authorizationProvider = plugin.getAuthorizationProvider();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLegacyChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!authorizationProvider.isAuthorized(player)
                || authorizationProvider.isAwaiting2FA(player)) {
            event.setCancelled(true);
        }
    }
}
