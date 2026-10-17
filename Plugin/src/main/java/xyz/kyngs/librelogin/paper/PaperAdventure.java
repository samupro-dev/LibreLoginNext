/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import xyz.kyngs.librelogin.common.util.ComponentBridge;
import xyz.kyngs.librelogin.lib.adventure.text.Component;
import xyz.kyngs.librelogin.lib.adventure.title.Title;

/**
 * Delivers LibreLogin's relocated components on every supported Paper version.
 *
 * <p>Paper 1.16.5+ ships native Adventure: components are handed over as JSON through {@link
 * PaperNativeAdventure}. Paper 1.13 - 1.16.4 have no native Adventure, so components are sent as
 * section-sign legacy text (RGB from 1.16, downsampled before that).
 */
@SuppressWarnings("deprecation")
public final class PaperAdventure {

    private static final boolean NATIVE = detectNative();
    private static final boolean HEX_COLORS = detectHexColors();

    private PaperAdventure() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    private static boolean detectNative() {
        try {
            var audience = Class.forName("net.kyori.adventure.audience.Audience");
            Class.forName("net.kyori.adventure.text.serializer.gson.GsonComponentSerializer");
            return audience.isAssignableFrom(Player.class);
        } catch (ClassNotFoundException | LinkageError e) {
            return false;
        }
    }

    private static boolean detectHexColors() {
        try {
            net.md_5.bungee.api.ChatColor.class.getMethod("of", String.class);
            return true;
        } catch (NoSuchMethodException | LinkageError e) {
            return false;
        }
    }

    /**
     * Returns whether the server ships native Adventure (Paper 1.16.5+).
     *
     * @return {@code true} if native Adventure is used for delivery
     */
    public static boolean isNative() {
        return NATIVE;
    }

    /**
     * Serializes a component to legacy text suitable for this server version.
     *
     * @param component the component
     * @return the legacy text
     */
    public static String legacy(Component component) {
        return ComponentBridge.toLegacy(component, HEX_COLORS);
    }

    /**
     * Sends a chat message.
     *
     * @param sender the receiver
     * @param message the message
     */
    public static void sendMessage(CommandSender sender, Component message) {
        if (NATIVE) {
            PaperNativeAdventure.sendMessage(sender, message);
        } else {
            sender.sendMessage(legacy(message));
        }
    }

    /**
     * Sends an action bar message to a player.
     *
     * @param player the player
     * @param message the message
     */
    public static void sendActionBar(Player player, Component message) {
        if (NATIVE) {
            PaperNativeAdventure.sendActionBar(player, message);
        } else {
            player.spigot()
                    .sendMessage(
                            ChatMessageType.ACTION_BAR,
                            TextComponent.fromLegacyText(legacy(message)));
        }
    }

    /**
     * Shows a title to a player.
     *
     * @param player the player
     * @param title the title
     */
    public static void showTitle(Player player, Title title) {
        if (NATIVE) {
            try {
                PaperNativeAdventure.showTitle(player, title);
                return;
            } catch (LinkageError ignored) {
                // Title.Times#times needs Adventure 4.10 (Paper 1.18+); use the legacy title.
            }
        }
        var times = title.times();
        int fadeIn = times == null ? 10 : ticks(times.fadeIn());
        int stay = times == null ? 70 : ticks(times.stay());
        int fadeOut = times == null ? 20 : ticks(times.fadeOut());
        player.sendTitle(legacy(title.title()), legacy(title.subtitle()), fadeIn, stay, fadeOut);
    }

    /**
     * Clears the title currently shown to a player.
     *
     * @param player the player
     */
    public static void clearTitle(Player player) {
        if (NATIVE) {
            PaperNativeAdventure.clearTitle(player);
        } else {
            player.resetTitle();
        }
    }

    /**
     * Kicks a player. Must be called on the main thread.
     *
     * @param player the player
     * @param reason the kick reason
     */
    public static void kick(Player player, Component reason) {
        if (NATIVE) {
            try {
                PaperNativeAdventure.kick(player, reason);
                return;
            } catch (LinkageError ignored) {
                // Player#kick(Component) is missing on the earliest native builds.
            }
        }
        player.kickPlayer(legacy(reason));
    }

    /**
     * Denies a pre-login with the given reason.
     *
     * @param event the pre-login event
     * @param result the result to set
     * @param reason the kick reason
     */
    public static void disallow(
            AsyncPlayerPreLoginEvent event,
            AsyncPlayerPreLoginEvent.Result result,
            Component reason) {
        if (NATIVE) {
            try {
                PaperNativeAdventure.disallow(event, result, reason);
                return;
            } catch (LinkageError ignored) {
                // Fall back to the legacy String overload below.
            }
        }
        event.disallow(result, legacy(reason));
    }

    private static int ticks(java.time.Duration duration) {
        return duration == null ? 0 : (int) Math.min(Integer.MAX_VALUE, duration.toMillis() / 50);
    }
}
