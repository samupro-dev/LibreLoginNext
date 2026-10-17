/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import xyz.kyngs.librelogin.common.util.PlatformAudience;
import xyz.kyngs.librelogin.lib.adventure.text.Component;
import xyz.kyngs.librelogin.lib.adventure.title.Title;

/**
 * Relocated {@link xyz.kyngs.librelogin.lib.adventure.audience.Audience} for a Bukkit command
 * sender (player or console), delivered through {@link PaperAdventure}.
 */
public final class PaperAudience implements PlatformAudience {

    private final CommandSender target;

    /**
     * Wraps a Bukkit command sender.
     *
     * @param target the sender
     */
    public PaperAudience(CommandSender target) {
        this.target = target;
    }

    @Override
    public Object target() {
        return target;
    }

    @Override
    public void sendMessage(@NotNull Component message) {
        PaperAdventure.sendMessage(target, message);
    }

    @Override
    public void sendActionBar(@NotNull Component message) {
        if (target instanceof Player player) {
            PaperAdventure.sendActionBar(player, message);
        }
    }

    @Override
    public void showTitle(@NotNull Title title) {
        if (target instanceof Player player) {
            PaperAdventure.showTitle(player, title);
        }
    }

    @Override
    public void clearTitle() {
        if (target instanceof Player player) {
            PaperAdventure.clearTitle(player);
        }
    }

    @Override
    public void resetTitle() {
        if (target instanceof Player player) {
            player.resetTitle();
        }
    }
}
