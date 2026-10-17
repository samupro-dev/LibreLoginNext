/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper;

import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import xyz.kyngs.librelogin.api.authorization.AuthorizationProvider;

/** Modern Paper chat blocker utilizing {@link AsyncChatEvent} on Paper 1.16.5 through 26.3. */
public class ModernPaperChatBlocker implements Listener {

    private final AuthorizationProvider<Player> authorizationProvider;

    public ModernPaperChatBlocker(PaperLibreLogin plugin) {
        this.authorizationProvider = plugin.getAuthorizationProvider();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        if (!authorizationProvider.isAuthorized(player)
                || authorizationProvider.isAwaiting2FA(player)) {
            event.setCancelled(true);
        }
    }
}
