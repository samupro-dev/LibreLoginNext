/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.crypto;

import org.jetbrains.annotations.Nullable;

/** Service provider interface for password hashing and verification algorithms. */
public interface CryptoProvider {

    /**
     * Hashes a plaintext password according to this provider's algorithm.
     *
     * @param password the plaintext password to hash
     * @return the resulting {@link HashedPassword}, or null if hashing failed
     */
    @Nullable
    HashedPassword createHash(String password);

    /**
     * Verifies a plaintext input against an existing stored {@link HashedPassword}.
     *
     * @param input the plaintext input to verify
     * @param password the stored hashed password record
     * @return true if the password matches the hash, false otherwise
     */
    boolean matches(String input, HashedPassword password);

    /**
     * Gets the unique identifier of the cryptographic algorithm (e.g. "BCRYPT", "ARGON2ID").
     *
     * @return the algorithm identifier
     */
    String getIdentifier();
}
