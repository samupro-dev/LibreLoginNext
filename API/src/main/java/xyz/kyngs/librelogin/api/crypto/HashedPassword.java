/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.crypto;

import org.jetbrains.annotations.Nullable;

/**
 * Immutable record representing a hashed password credential, optional salt, and algorithm
 * identifier.
 *
 * @param hash the hash digest string
 * @param salt the optional salt value, or null if self-contained within the hash
 * @param algo the identifier of the algorithm used to create the hash
 */
public record HashedPassword(String hash, @Nullable String salt, String algo) {

    @Override
    public String toString() {
        return "HashedPassword{"
                + "hash='[PROTECTED]'"
                + ", salt="
                + (salt != null ? "'[PROTECTED]'" : "null")
                + ", algo='"
                + algo
                + '\''
                + '}';
    }
}
