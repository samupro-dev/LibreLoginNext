/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.password4j.Argon2Function;
import com.password4j.BcryptFunction;
import com.password4j.Password;
import com.password4j.types.Argon2;
import com.password4j.types.Bcrypt;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import xyz.kyngs.librelogin.api.Logger;
import xyz.kyngs.librelogin.api.crypto.HashedPassword;

class LegacyHashesTest {

    private static final Logger NO_OP =
            new Logger() {
                @Override
                public void info(String message) {}

                @Override
                public void info(String message, Throwable throwable) {}

                @Override
                public void warn(String message) {}

                @Override
                public void warn(String message, Throwable throwable) {}

                @Override
                public void error(String message) {}

                @Override
                public void error(String message, Throwable throwable) {}

                @Override
                public void debug(String message) {}

                @Override
                public void debug(String message, Throwable throwable) {}
            };

    private final LegacyMessageDigestCryptoProvider sha256 =
            new LegacyMessageDigestCryptoProvider("SHA-256", "SHA-256", false);

    // Vectors from AuthMeReloaded 6.0.1 Sha256Test (auth_sources/AuthMeReloaded-6.0.1).
    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "$SHA$11aa0706173d7272$dbba96681c2ae4e0bfdf226d70fbbc5e4ee3d8071faa613bc533fe8a64817d10|password",
                "$SHA$3c72a18a29b08d40$8e50a7a4f69a80f4893dc921eac84bd74b3f9ebfa22908302c9965eac3aa45e5|PassWord1",
                "$SHA$584cea1cfab90030$adc006330e73d81e463fe02a4fe9b17bdbbcc05955bff72fb27cf2089f0b3859|&^%te$t?Pw@_",
                "$SHA$0b503d90dd9949d4$ba70c330242e0daa9a154ec9f4cce7f01dd05aff489d37c653e36a507c74d84f|âË_3(íù*",
            })
    void verifiesAuthMeSha256(String stored, String password) {
        var hashed = LegacyHashNormalizer.fromAuthMeSha256(stored);
        assertNotNull(hashed);
        assertEquals("SHA-256", hashed.algo());
        assertTrue(sha256.matches(password, hashed));
        assertFalse(sha256.matches(password + "x", hashed));
    }

    @Test
    void rejectsMalformedAuthMeValues() {
        assertNull(LegacyHashNormalizer.fromAuthMeSha256("$SHA$onlysalt"));
        assertNull(LegacyHashNormalizer.fromAuthMeSha256("pbkdf2_sha256$10000$salt$hash"));
        assertNull(LegacyHashNormalizer.fromAuthMeSha256(null));
    }

    @Test
    void legacyProvidersNeverCreateHashes() {
        assertNull(sha256.createHash("anything"));
    }

    @Test
    void verifiesLibreLoginBcryptTuple() {
        var mcf =
                Password.hash("hunter2").with(BcryptFunction.getInstance(Bcrypt.A, 10)).getResult();
        // LibreLogin stored "$2a$10$<22 salt><31 hash>" as hash="10$<31 hash>", salt="<22 salt>".
        var rest = mcf.substring("$2a$10$".length());
        var tuple =
                new HashedPassword("10$" + rest.substring(22), rest.substring(0, 22), "BCrypt-2A");

        var normalized = LegacyHashNormalizer.normalize(tuple);
        assertEquals(mcf, normalized.hash());
        assertNull(normalized.salt());

        var provider = new BCrypt2ACryptoProvider();
        assertTrue(provider.matches("hunter2", normalized));
        assertFalse(provider.matches("hunter3", normalized));
    }

    @Test
    void verifiesLibreLoginArgon2Tuple() {
        var function = Argon2Function.getInstance(16384, 2, 1, 32, Argon2.ID, 19);
        var mcf = Password.hash("hunter2").addRandomSalt(16).with(function).getResult();
        // $argon2id$v=19$m=16384,t=2,p=1$<salt>$<hash>
        var parts = mcf.split("\\$");
        var padded = Base64.getEncoder();
        var salt = padded.encodeToString(Base64.getDecoder().decode(parts[4]));
        var hash = padded.encodeToString(Base64.getDecoder().decode(parts[5]));
        var tuple = new HashedPassword("19,2,16384$" + hash, salt, "Argon-2ID");

        var normalized = LegacyHashNormalizer.normalize(tuple);
        assertEquals(mcf, normalized.hash());

        var provider = new Argon2IDCryptoProvider(NO_OP);
        assertTrue(provider.matches("hunter2", normalized));
        assertFalse(provider.matches("hunter3", normalized));
    }

    @Test
    void leavesModernAndUnknownValuesUntouched() {
        var mcf =
                new HashedPassword(
                        "$2a$10$abcdefghijklmnopqrstuuabcdefghijklmnopqrstuvwxyz12345",
                        null,
                        "BCrypt-2A");
        assertSame(mcf, LegacyHashNormalizer.normalize(mcf));
        var sha = new HashedPassword("abcdef", "salt", "SHA-256");
        assertSame(sha, LegacyHashNormalizer.normalize(sha));
        assertNull(LegacyHashNormalizer.normalize(null));
    }
}
