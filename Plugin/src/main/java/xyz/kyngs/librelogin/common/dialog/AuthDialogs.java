/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.dialog;

import java.util.UUID;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.lib.adventure.text.TextComponent;

/**
 * Login and registration dialogs.
 *
 * <p>Fields and method signatures of the plugin classes use this type, never the PacketEvents
 * implementation: Velocity's injector reads them, which would load PacketEvents classes and fail
 * when PacketEvents (optional there) is not installed.
 *
 * @param <P> the platform player type
 */
public interface AuthDialogs<P> {

    /** Starts listening for dialog answers. */
    void register();

    /** Stops listening for dialog answers and forgets every open dialog. */
    void unregister();

    /**
     * Shows the login or registration dialog. Does nothing when the client cannot show dialogs.
     *
     * @param player the player
     * @param login true for the login dialog, false for the registration dialog
     * @param error an error to show above the form, or {@code null}
     * @return true if the dialog was sent
     */
    boolean show(P player, boolean login, @Nullable TextComponent error);

    /**
     * Shows the change password dialog (opened by /changepassword without arguments).
     *
     * @param player the player
     * @return true if the dialog was sent
     */
    boolean showChangePassword(P player);

    /**
     * Shows the unregister dialog (opened by /unregister without arguments).
     *
     * @param player the player
     * @return true if the dialog was sent
     */
    boolean showUnregister(P player);

    /**
     * Closes the dialog of a player who was shown one.
     *
     * @param player the player
     */
    void clear(P player);

    /**
     * Forgets a player who left.
     *
     * @param uuid the player's UUID
     */
    void forget(UUID uuid);

    /**
     * Whether a player currently has a login or registration dialog open.
     *
     * @param uuid the player's UUID
     * @return true if a dialog is open
     */
    boolean isOpen(UUID uuid);
}
