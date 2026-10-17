/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import xyz.kyngs.librelogin.api.Logger;
import xyz.kyngs.librelogin.api.util.SemanticVersion;
import xyz.kyngs.librelogin.common.crypto.BCrypt2ACryptoProvider;

class HardeningTest {

    @ParameterizedTest
    @ValueSource(
            strings = {"steve@example.com", "a.b+tag@mail.example.co.uk", " padded@example.org "})
    void acceptsEmails(String email) {
        assertTrue(GeneralUtil.isValidEmail(email), email);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "",
                "steve",
                "steve@",
                "@example.com",
                "a@b",
                "a b@example.com",
                "a@@example.com"
            })
    void rejectsNonEmails(String email) {
        assertFalse(GeneralUtil.isValidEmail(email), email);
    }

    @Test
    void escapesHtml() {
        assertEquals("&lt;b&gt;&amp;&quot;&#39;", GeneralUtil.escapeHtml("<b>&\"'"));
        assertEquals("", GeneralUtil.escapeHtml(null));
    }

    @Test
    void bcryptRefusesPasswordsLongerThan72Bytes() {
        var provider = new BCrypt2ACryptoProvider();
        assertNotNull(provider.createHash("a".repeat(72)));
        assertNull(provider.createHash("a".repeat(73)));
        // Multi-byte characters count in UTF-8 bytes: 25 x 3 bytes = 75
        assertNull(provider.createHash("€".repeat(25)));
    }

    @Test
    void loggerDefaultsFormatPlaceholders() {
        assertEquals("a=1 b=x", Logger.formatPlaceholders("a={} b={}", 1, "x"));
        assertEquals("only {}", Logger.formatPlaceholders("only {}", new RuntimeException()));
    }

    @Test
    void semanticVersionDoesNotOverflow() {
        var version = SemanticVersion.parse("99999999999.2.3");
        assertEquals(Integer.MAX_VALUE, version.major());
        assertEquals(2, version.minor());
    }
}
