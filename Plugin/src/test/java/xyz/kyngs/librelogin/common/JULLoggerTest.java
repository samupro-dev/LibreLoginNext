/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class JULLoggerTest {

    @Test
    void substitutesPlaceholdersInOrder() {
        assertEquals("a 1 b 2", JULLogger.format("a {} b {}", new Object[] {1, 2}, 2));
    }

    @Test
    void leavesExtraPlaceholdersAndIgnoresTrailingThrowable() {
        var cause = new RuntimeException("x");
        // The caller passes usable = 1 when the last argument is the Throwable.
        assertEquals("v=7 w={}", JULLogger.format("v={} w={}", new Object[] {7, cause}, 1));
    }
}
