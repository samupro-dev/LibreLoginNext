/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.command.commands.tfa;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class GroupSecretTest {

    @Test
    void groupsTheKeyInBlocksOfFour() {
        assertEquals(
                "DT5Y V23M VIWL II7G KKAW 6UQ7 RULB XJ2E",
                TwoFactorCommand.groupSecret("DT5YV23MVIWLII7GKKAW6UQ7RULBXJ2E"));
    }

    @Test
    void leavesNoTrailingSpaceOnShortLastGroup() {
        assertEquals("ABCD EF", TwoFactorCommand.groupSecret("ABCDEF"));
        assertEquals("ABCD", TwoFactorCommand.groupSecret("ABCD"));
    }
}
