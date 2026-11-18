/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.dialog;

import com.github.retrooper.packetevents.protocol.nbt.NBT;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import org.jetbrains.annotations.Nullable;

/**
 * Values a player submitted in a login or registration dialog.
 *
 * <p>The payload comes from the client and can be forged, so only string values of a bounded length
 * are accepted; anything else reads as missing.
 *
 * @param password the password, or {@code null} if missing or invalid
 * @param passwordRepeat the repeated password, or {@code null} if missing or invalid
 * @param code the 2FA code, or {@code null} if missing, blank or invalid
 * @param oldPassword the current password (change password dialog), or {@code null}
 * @param token the code received by email (password reset dialog), or {@code null}
 */
public record DialogPayload(
        @Nullable String password,
        @Nullable String passwordRepeat,
        @Nullable String code,
        @Nullable String oldPassword,
        @Nullable String token) {

    static final String PASSWORD = "password";
    static final String PASSWORD_REPEAT = "password_repeat";
    static final String CODE = "code";
    static final String OLD_PASSWORD = "old_password";
    static final String TOKEN = "token";
    static final int MAX_TOKEN_LENGTH = 64;
    static final int MAX_PASSWORD_LENGTH = 256;
    static final int MAX_CODE_LENGTH = 16;

    /**
     * Reads the submitted values from a custom click action payload.
     *
     * @param payload the NBT payload sent by the client
     * @return the submitted values
     */
    public static DialogPayload read(@Nullable NBT payload) {
        if (!(payload instanceof NBTCompound compound)) {
            return new DialogPayload(null, null, null, null, null);
        }
        var code = string(compound, CODE, MAX_CODE_LENGTH);
        return new DialogPayload(
                string(compound, PASSWORD, MAX_PASSWORD_LENGTH),
                string(compound, PASSWORD_REPEAT, MAX_PASSWORD_LENGTH),
                code == null || code.isBlank() ? null : code,
                string(compound, OLD_PASSWORD, MAX_PASSWORD_LENGTH),
                string(compound, TOKEN, MAX_TOKEN_LENGTH));
    }

    private static @Nullable String string(NBTCompound compound, String key, int maxLength) {
        var value = compound.getStringTagValueOrNull(key);
        return value == null || value.length() > maxLength ? null : value;
    }
}
