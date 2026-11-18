/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.dialog;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.retrooper.packetevents.manager.server.ServerVersion;
import com.github.retrooper.packetevents.protocol.nbt.NBTCompound;
import com.github.retrooper.packetevents.protocol.nbt.NBTInt;
import com.github.retrooper.packetevents.protocol.nbt.NBTString;
import org.junit.jupiter.api.Test;

class DialogPayloadTest {

    @Test
    void readsSubmittedStrings() {
        var compound = new NBTCompound();
        compound.setTag("password", new NBTString("Secret_123"));
        compound.setTag("password_repeat", new NBTString("Secret_123"));
        compound.setTag("code", new NBTString("123 456"));

        var payload = DialogPayload.read(compound);

        assertEquals("Secret_123", payload.password());
        assertEquals("Secret_123", payload.passwordRepeat());
        assertEquals("123 456", payload.code());
    }

    @Test
    void treatsMissingWrongTypeTooLongAndBlankValuesAsMissing() {
        var compound = new NBTCompound();
        compound.setTag("password", new NBTInt(5));
        compound.setTag(
                "password_repeat",
                new NBTString("x".repeat(DialogPayload.MAX_PASSWORD_LENGTH + 1)));
        compound.setTag("code", new NBTString("   "));

        var payload = DialogPayload.read(compound);

        assertNull(payload.password());
        assertNull(payload.passwordRepeat());
        assertNull(payload.code());
        assertNull(DialogPayload.read(null).password());
        assertNull(DialogPayload.read(new NBTString("not a compound")).password());
    }

    @Test
    void supportsDialogsFrom1216() {
        assertTrue(
                PacketEventsAuthDialogs.supports(
                        com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_21_6,
                        null));
        assertTrue(
                PacketEventsAuthDialogs.supports(
                        com.github.retrooper.packetevents.protocol.player.ClientVersion.V_26_3,
                        ServerVersion.V_1_21_11));
        assertFalse(
                PacketEventsAuthDialogs.supports(
                        com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_21_5,
                        null));
        assertFalse(
                PacketEventsAuthDialogs.supports(
                        com.github.retrooper.packetevents.protocol.player.ClientVersion.V_1_21_11,
                        ServerVersion.V_1_21_5));
        assertFalse(PacketEventsAuthDialogs.supports(null, null));
    }
}
