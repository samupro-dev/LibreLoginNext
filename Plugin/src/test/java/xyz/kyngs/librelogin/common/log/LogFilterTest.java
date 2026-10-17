/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.log;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LogFilterTest {

    private final LogFilter filter =
            new LogFilter() {
                @Override
                public void inject() {}

                @Override
                public void remove() {}
            };

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Steve issued server command: /login hunter2",
                "Steve issued server command: /l hunter2",
                "Steve issued server command: /LOGIN hunter2",
                "Steve issued server command: /register hunter2 hunter2",
                "Steve issued server command: /reg hunter2 hunter2",
                "Steve issued server command: /unregister hunter2",
                "Steve issued server command: /cracked hunter2 hunter2",
                "Steve issued server command: /changepassword old new",
                "Steve issued server command: /changepass old new",
                "Steve issued server command: /premium hunter2",
                "Steve issued server command: /2fa disable hunter2",
                "Steve issued server command: /totp confirm 123456",
                "Steve issued server command: /email set a@b.c hunter2",
                "Steve issued server command: /librelogin:login hunter2",
                "Steve issued server command: /librelogin:unregister hunter2",
                "Steve issued server command: /LibreLogin:register a a",
                "Admin issued server command: /librelogin user register Bob pw",
                "Admin issued server command: /librelogin user setpassword Bob pw",
                "Admin issued server command: /librelogin user setpassword Bob pw",
                "Admin issued server command: /librelogin:librelogin user setpassword Bob pw",
                "Admin issued server command: /librelogin user setemail Bob a@b.c",
                "[connected player] Steve (/1.2.3.4:5) -> executed command /login hunter2",
            })
    void hidesCredentialCommands(String line) {
        assertFalse(filter.checkMessage(line), line);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "Steve issued server command: /spawn",
                "Steve issued server command: /logout",
                "Admin issued server command: /librelogin user info Bob",
                "Admin issued server command: /librelogin reload",
                "Steve joined the game",
            })
    void keepsOrdinaryLines(String line) {
        assertTrue(filter.checkMessage(line), line);
    }
}
