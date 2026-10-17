/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper;

import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import xyz.kyngs.librelogin.common.util.ComponentBridge;
import xyz.kyngs.librelogin.lib.adventure.text.Component;
import xyz.kyngs.librelogin.lib.adventure.title.Title;

/**
 * Native Adventure delivery for Paper 1.16.5+. Only loaded after {@link PaperAdventure#isNative()}
 * confirmed the server ships Adventure, so older servers never link these references.
 */
final class PaperNativeAdventure {

    private PaperNativeAdventure() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    static net.kyori.adventure.text.Component toNative(Component component) {
        return GsonComponentSerializer.gson().deserialize(ComponentBridge.toJson(component));
    }

    static void sendMessage(CommandSender sender, Component message) {
        ((net.kyori.adventure.audience.Audience) sender).sendMessage(toNative(message));
    }

    static void sendActionBar(Player player, Component message) {
        player.sendActionBar(toNative(message));
    }

    static void showTitle(Player player, Title title) {
        var times = title.times();
        var main = toNative(title.title());
        var sub = toNative(title.subtitle());
        player.showTitle(
                times == null
                        ? net.kyori.adventure.title.Title.title(main, sub)
                        : net.kyori.adventure.title.Title.title(
                                main,
                                sub,
                                net.kyori.adventure.title.Title.Times.times(
                                        times.fadeIn(), times.stay(), times.fadeOut())));
    }

    static void clearTitle(Player player) {
        player.clearTitle();
    }

    static void kick(Player player, Component reason) {
        player.kick(toNative(reason));
    }

    static void disallow(
            AsyncPlayerPreLoginEvent event,
            AsyncPlayerPreLoginEvent.Result result,
            Component reason) {
        event.disallow(result, toNative(reason));
    }
}
