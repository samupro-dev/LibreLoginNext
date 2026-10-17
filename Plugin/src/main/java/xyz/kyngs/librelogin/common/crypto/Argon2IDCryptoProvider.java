/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.crypto;

import com.password4j.Argon2Function;
import com.password4j.Hash;
import com.password4j.Password;
import com.password4j.types.Argon2;
import java.util.concurrent.Semaphore;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.Logger;
import xyz.kyngs.librelogin.api.crypto.CryptoProvider;
import xyz.kyngs.librelogin.api.crypto.HashedPassword;

/**
 * Implementation of {@link CryptoProvider} utilizing the Argon2id key derivation function via
 * Password4j.
 *
 * <p>Argon2id is a hybrid password-hashing function combining data-dependent memory access
 * (Argon2d) for GPU-cracking resistance and data-independent memory access (Argon2i) for
 * side-channel attack resistance.
 *
 * <p>Standard Modular Crypt Format (MCF) hashes ($argon2id$v=19$m=16384,t=2,p=1$...) are produced
 * and verified.
 */
public class Argon2IDCryptoProvider implements CryptoProvider {

    private static final int DEFAULT_MEMORY_KB = 16384; // 16 MB
    private static final int DEFAULT_ITERATIONS = 2;
    private static final int DEFAULT_PARALLELISM = 1;
    private static final int DEFAULT_OUTPUT_LENGTH = 32;

    private final Logger logger;
    private final Argon2Function defaultFunction;

    /**
     * Constructs a new {@code Argon2IDCryptoProvider} with the provided logger.
     *
     * @param logger the logger used for performance diagnostics
     */
    /**
     * Each Argon2 computation allocates its full memory cost (16 MiB by default). Bounding the
     * concurrent computations to the CPU count bounds that memory during a login burst; extra
     * callers wait, which adds no latency because the CPUs are busy anyway.
     */
    private static final Semaphore CONCURRENT_HASHES =
            new Semaphore(Math.max(2, Runtime.getRuntime().availableProcessors()));

    public Argon2IDCryptoProvider(Logger logger) {
        this.logger = logger;
        this.defaultFunction =
                Argon2Function.getInstance(
                        DEFAULT_MEMORY_KB,
                        DEFAULT_ITERATIONS,
                        DEFAULT_PARALLELISM,
                        DEFAULT_OUTPUT_LENGTH,
                        Argon2.ID,
                        Argon2Function.ARGON2_VERSION_13);
    }

    @Nullable
    @Override
    public HashedPassword createHash(String password) {
        if (password == null) {
            return null;
        }
        var start = System.currentTimeMillis();
        Hash hash;
        CONCURRENT_HASHES.acquireUninterruptibly();
        try {
            hash = Password.hash(password).with(defaultFunction);
        } finally {
            CONCURRENT_HASHES.release();
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Argon2ID hash took {}ms", System.currentTimeMillis() - start);
        }

        return new HashedPassword(hash.getResult(), null, getIdentifier());
    }

    @Override
    public boolean matches(String input, HashedPassword password) {
        if (input == null || password == null || password.hash() == null) {
            return false;
        }

        try {
            var argon2 = Argon2Function.getInstanceFromHash(password.hash());
            CONCURRENT_HASHES.acquireUninterruptibly();
            try {
                return Password.check(input, password.hash()).with(argon2);
            } finally {
                CONCURRENT_HASHES.release();
            }
        } catch (Exception e) {
            logger.warn("Failed to verify Argon2id password hash: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getIdentifier() {
        return "Argon-2ID";
    }
}
