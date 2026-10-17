/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.crypto;

import com.password4j.BcryptFunction;
import com.password4j.Hash;
import com.password4j.Password;
import com.password4j.types.Bcrypt;
import java.nio.charset.StandardCharsets;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.crypto.CryptoProvider;
import xyz.kyngs.librelogin.api.crypto.HashedPassword;

/**
 * Implementation of {@link CryptoProvider} utilizing the BCrypt password hashing scheme via
 * Password4j.
 *
 * <p>This provider uses a work cost factor of 10 (1024 key expansion iterations). The underlying
 * verification routine performs constant-time byte comparison to eliminate timing-attack
 * vulnerabilities.
 *
 * <p>Standard Modular Crypt Format (MCF) hashes ($2a, $2b, $2y) are produced and verified directly.
 */
public class BCrypt2ACryptoProvider implements CryptoProvider {

    private static final int DEFAULT_LOG_ROUNDS = 10;

    /** BCrypt ignores everything after 72 bytes; longer passwords would collide silently. */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final BcryptFunction defaultFunction;

    /** Constructs a new {@code BCrypt2ACryptoProvider} with standard cost 10 and $2a prefix. */
    public BCrypt2ACryptoProvider() {
        this.defaultFunction = BcryptFunction.getInstance(Bcrypt.A, DEFAULT_LOG_ROUNDS);
    }

    @Override
    @Nullable
    public HashedPassword createHash(String password) {
        if (password == null
                || password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            // null makes the caller report error.password-too-long
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
            BcryptFunction verifier = BcryptFunction.getInstanceFromHash(hash);
            return Password.check(input, hash).with(verifier);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public String getIdentifier() {
        return "BCrypt-2A";
    }
}
