/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.crypto;

import com.password4j.Hash;
import com.password4j.Password;
import com.password4j.ScryptFunction;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.crypto.CryptoProvider;
import xyz.kyngs.librelogin.api.crypto.HashedPassword;

/**
 * Implementation of {@link CryptoProvider} utilizing the SCrypt memory-hard key derivation function
 * via Password4j.
 *
 * <p>SCrypt is specifically designed to make large-scale custom hardware attacks difficult by
 * requiring large amounts of memory.
 */
public class SCryptCryptoProvider implements CryptoProvider {

    private static final int DEFAULT_WORK_FACTOR = 16384; // N (1 << 14)
    private static final int DEFAULT_RESOURCES = 8; // r
    private static final int DEFAULT_PARALLELIZATION = 1; // p
    private static final int DEFAULT_KEY_LENGTH = 32; // dkLen

    private final ScryptFunction defaultFunction;

    /** Constructs a new {@code SCryptCryptoProvider} with standard OWASP-recommended parameters. */
    public SCryptCryptoProvider() {
        this.defaultFunction =
                ScryptFunction.getInstance(
                        DEFAULT_WORK_FACTOR,
                        DEFAULT_RESOURCES,
                        DEFAULT_PARALLELIZATION,
                        DEFAULT_KEY_LENGTH);
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
                    ScryptFunction function = ScryptFunction.getInstanceFromHash(hash);
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
        return "SCrypt";
    }
}
