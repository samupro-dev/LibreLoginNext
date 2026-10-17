/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.totp;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.helger.totp.code.DefaultCodeGenerator;
import com.helger.totp.secret.DefaultSecretGenerator;
import org.junit.jupiter.api.Test;

class TotpReplayTest {

    // verify() never touches the plugin; it is only needed to generate QR codes.
    private final AuthenticTOTPProvider provider = new AuthenticTOTPProvider(null);
    private final DefaultCodeGenerator generator = new DefaultCodeGenerator();

    private int code(String secret, long slot) throws Exception {
        return Integer.parseInt(generator.generate(secret, slot));
    }

    private static long currentSlot() {
        return Math.floorDiv(System.currentTimeMillis() / 1000L, 30L);
    }

    @Test
    void codeIsAcceptedOnlyOnce() throws Exception {
        var secret = new DefaultSecretGenerator().generate();
        long slot = currentSlot();
        assertTrue(provider.verify(code(secret, slot), secret));
        assertFalse(provider.verify(code(secret, slot), secret), "replayed code accepted");
    }

    @Test
    void olderCodeIsRejectedAfterNewerOne() throws Exception {
        var secret = new DefaultSecretGenerator().generate();
        long slot = currentSlot();
        assertTrue(provider.verify(code(secret, slot), secret));
        // The previous slot is still inside the verifier's +/-1 window, but it is older than the
        // slot already used, so it must not be accepted (previously possible across a boundary).
        assertFalse(provider.verify(code(secret, slot - 1), secret));
    }

    @Test
    void rejectsWrongCodesAndInput() {
        var secret = new DefaultSecretGenerator().generate();
        assertFalse(provider.verify(-1, secret));
        assertFalse(provider.verify(1_000_000, secret));
        assertFalse(provider.verify(null, secret));
        assertFalse(provider.verify(123456, null));
    }
}
