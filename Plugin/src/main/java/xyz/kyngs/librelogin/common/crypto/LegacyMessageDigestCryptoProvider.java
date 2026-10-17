/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.crypto;

import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.crypto.HashedPassword;

/**
 * Verify-only provider for salted message-digest hashes imported from other plugins:
 *
 * <ul>
 *   <li>{@code SHA-256} / {@code SHA-512}: {@code hex(H(hex(H(password)) + salt))}, or {@code
 *       hex(H(password))} without salt. This is AuthMe's {@code $SHA$} scheme and the format stored
 *       by LibreLogin.
 *   <li>{@code LOGIT-SHA-256}: {@code hex(H(password + salt))}.
 * </ul>
 *
 * <p>It never creates hashes: a successful login re-hashes the password with the configured default
 * provider (see {@code LoginCommand}). Hex strings are formatted with {@code %064x} exactly like
 * the originating plugins. Those plugins hashed with the JVM default charset, so UTF-8 is tried
 * first and the default charset second.
 */
public class LegacyMessageDigestCryptoProvider implements VerifyOnlyCryptoProvider {

    private final String identifier;
    private final String digest;
    private final boolean saltAppendedToPassword;

    /**
     * Constructs a new verify-only provider.
     *
     * @param identifier the stored algorithm identifier
     * @param digest the {@link MessageDigest} algorithm name
     * @param saltAppendedToPassword {@code true} for LOGIT's {@code H(password + salt)} variant
     */
    public LegacyMessageDigestCryptoProvider(
            String identifier, String digest, boolean saltAppendedToPassword) {
        this.identifier = identifier;
        this.digest = digest;
        this.saltAppendedToPassword = saltAppendedToPassword;
        newDigest(); // fail fast if the JVM lacks the algorithm
    }

    @Override
    @Nullable
    public HashedPassword createHash(String password) {
        return null;
    }

    @Override
    public boolean matches(String input, HashedPassword password) {
        if (input == null || password == null || password.hash() == null) {
            return false;
        }
        var expected = password.hash().toLowerCase(Locale.ROOT);
        if (matches(input, password.salt(), expected, StandardCharsets.UTF_8)) {
            return true;
        }
        var platform = Charset.defaultCharset();
        return !platform.equals(StandardCharsets.UTF_8)
                && matches(input, password.salt(), expected, platform);
    }

    private boolean matches(String input, @Nullable String salt, String expected, Charset charset) {
        String computed;
        if (salt == null) {
            computed = hex(input, charset);
        } else if (saltAppendedToPassword) {
            computed = hex(input + salt, charset);
        } else {
            computed = hex(hex(input, charset) + salt, charset);
        }
        return MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.US_ASCII),
                expected.getBytes(StandardCharsets.US_ASCII));
    }

    private String hex(String value, Charset charset) {
        return String.format(
                "%064x", new BigInteger(1, newDigest().digest(value.getBytes(charset))));
    }

    private MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Missing message digest " + digest, e);
        }
    }

    @Override
    public String getIdentifier() {
        return identifier;
    }
}
