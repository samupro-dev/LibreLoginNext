/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.velocity;

import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.jetbrains.annotations.NotNull;
import xyz.kyngs.librelogin.common.util.ComponentBridge;
import xyz.kyngs.librelogin.common.util.PlatformAudience;
import xyz.kyngs.librelogin.lib.adventure.text.Component;
import xyz.kyngs.librelogin.lib.adventure.title.Title;

/**
 * Delivers relocated components to a Velocity (native Adventure) audience such as a player or the
 * console. Conversion goes through JSON, so it works with any native Adventure major version.
 */
public final class VelocityAudience implements PlatformAudience {

    private final net.kyori.adventure.audience.Audience target;

    /**
     * Wraps a native Velocity audience.
     *
     * @param target the native audience (player or command source)
     */
    public VelocityAudience(net.kyori.adventure.audience.Audience target) {
        this.target = target;
    }

    /**
     * Converts a relocated component to the proxy's native component.
     *
     * @param component the relocated component
     * @return the native component
     */
    public static net.kyori.adventure.text.Component toNative(Component component) {
        return GsonComponentSerializer.gson().deserialize(ComponentBridge.toJson(component));
    }

    /**
     * Converts a native component (for example a backend kick reason) to a relocated component.
     *
     * @param component the native component
     * @return the relocated component
     */
    public static Component fromNative(net.kyori.adventure.text.Component component) {
        return ComponentBridge.fromJson(GsonComponentSerializer.gson().serialize(component));
    }

    @Override
    public Object target() {
        return target;
    }

    @Override
    public void sendMessage(@NotNull Component message) {
        target.sendMessage(toNative(message));
    }

    @Override
    public void sendActionBar(@NotNull Component message) {
        target.sendActionBar(toNative(message));
    }

    @Override
    public void showTitle(@NotNull Title title) {
        var times = title.times();
        var main = toNative(title.title());
        var sub = toNative(title.subtitle());
        if (times != null) {
            try {
                target.showTitle(
                        net.kyori.adventure.title.Title.title(
                                main,
                                sub,
                                net.kyori.adventure.title.Title.Times.times(
                                        times.fadeIn(), times.stay(), times.fadeOut())));
                return;
            } catch (LinkageError ignored) {
                // Title.Times#times needs Adventure 4.10; early Velocity 3 builds use default
                // times.
            }
        }
        target.showTitle(net.kyori.adventure.title.Title.title(main, sub));
    }

    @Override
    public void clearTitle() {
        target.clearTitle();
    }

    @Override
    public void resetTitle() {
        target.resetTitle();
    }
}
