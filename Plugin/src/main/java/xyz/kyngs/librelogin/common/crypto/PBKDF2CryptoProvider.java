/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.crypto;

import com.password4j.CompressedPBKDF2Function;
import com.password4j.Hash;
import com.password4j.Password;
import com.password4j.types.Hmac;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.crypto.CryptoProvider;
import xyz.kyngs.librelogin.api.crypto.HashedPassword;

/**
 * Implementation of {@link CryptoProvider} utilizing the PBKDF2 (Password-Based Key Derivation
 * Function 2) standard via Password4j.
 *
 * <p>Configured with HMAC-SHA256 and 310,000 iterations according to current OWASP password storage
 * guidelines.
 */
public class PBKDF2CryptoProvider implements CryptoProvider {

    private static final int DEFAULT_ITERATIONS = 310000;
    private static final int DEFAULT_KEY_LENGTH = 32; // 256 bits

    private final CompressedPBKDF2Function defaultFunction;

    /**
     * Constructs a new {@code PBKDF2CryptoProvider} using HMAC-SHA256 and OWASP standard
     * iterations.
     */
    public PBKDF2CryptoProvider() {
        this.defaultFunction =
                CompressedPBKDF2Function.getInstance(
                        Hmac.SHA256, DEFAULT_ITERATIONS, DEFAULT_KEY_LENGTH);
    }

    @Override
    @Nullable
    public HashedPassword createHash(String password) {
        if (password == null) {
            return null;
        }
        try {
            Hash hash = Password.hash(password).with(defaultFunction);
            return new HashedPassword(hash.getResult(), null, getIdentifier());
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public boolean matches(String input, HashedPassword password) {
        if (input == null || password == null || password.hash() == null) {
            return false;
        }
        try {
            String hash = password.hash();
            if (hash.startsWith("$")) {
                try {
                    var function = CompressedPBKDF2Function.getInstanceFromHash(hash);
                    return Password.check(input, hash).with(function);
                } catch (Exception ignored) {
                }
            }
            if (password.salt() != null) {
                return Password.check(input, hash).addSalt(password.salt()).with(defaultFunction);
            }
            return Password.check(input, hash).with(defaultFunction);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getIdentifier() {
        return "PBKDF2";
    }
}
