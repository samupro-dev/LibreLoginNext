/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.crypto;

import java.util.Base64;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.crypto.HashedPassword;

/**
 * Converts hashes stored by LibreLogin (separate hash/salt columns) into the Modular Crypt Format
 * strings the BCrypt and Argon2 providers verify. Any other value is returned unchanged.
 *
 * <ul>
 *   <li>BCrypt: {@code hash="<cost>$<31 chars>"}, {@code salt="<22 chars>"}, {@code
 *       algo="BCrypt-2A|2B|2Y"} becomes {@code $2a$<cost>$<salt><hash>}.
 *   <li>Argon2id: {@code hash="<version>,<iterations>,<memoryKiB>$<base64>"}, {@code
 *       salt=<base64>}, {@code algo="Argon-2ID"} becomes {@code
 *       $argon2id$v=<version>$m=<memoryKiB>,t=<iterations>,p=1$<salt>$<hash>} (LibreLogin used a
 *       parallelism of 1).
 * </ul>
 */
public final class LegacyHashNormalizer {

    private LegacyHashNormalizer() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Normalizes a stored hash.
     *
     * @param password the stored hash, may be {@code null}
     * @return the normalized hash, or the input when no conversion applies
     */
    public static @Nullable HashedPassword normalize(@Nullable HashedPassword password) {
        if (password == null
                || password.hash() == null
                || password.salt() == null
                || password.algo() == null
                || password.hash().startsWith("$")) {
            return password;
        }

        try {
            var algo = password.algo();
            var separator = password.hash().indexOf('$');
            if (separator <= 0) {
                return password;
            }
            var parameters = password.hash().substring(0, separator);
            var digest = password.hash().substring(separator + 1);

            if (algo.startsWith("BCrypt-")) {
                var variant = algo.substring("BCrypt-".length()).toLowerCase(Locale.ROOT);
                if (password.salt().length() != 22
                        || digest.length() != 31
                        || !variant.matches("2[abxy]")
                        || !parameters.matches("\\d{1,2}")) {
                    return password;
                }
                var cost = parameters.length() == 1 ? "0" + parameters : parameters;
                return new HashedPassword(
                        "$" + variant + "$" + cost + "$" + password.salt() + digest,
                        null,
                        "BCrypt-2A");
            }

            if (algo.equals("Argon-2ID")) {
                var values = parameters.split(",");
                if (values.length != 3) {
                    return password;
                }
                int version = Integer.parseInt(values[0]);
                int iterations = Integer.parseInt(values[1]);
                int memory = Integer.parseInt(values[2]);
                var encoder = Base64.getEncoder().withoutPadding();
                var salt = encoder.encodeToString(Base64.getDecoder().decode(password.salt()));
                var hash = encoder.encodeToString(Base64.getDecoder().decode(digest));
                return new HashedPassword(
                        "$argon2id$v="
                                + version
                                + "$m="
                                + memory
                                + ",t="
                                + iterations
                                + ",p=1$"
                                + salt
                                + "$"
                                + hash,
                        null,
                        "Argon-2ID");
            }
        } catch (IllegalArgumentException e) {
            // Malformed numbers or Base64: keep the stored value, verification will fail safely.
        }
        return password;
    }

    /**
     * Converts an AuthMe {@code $SHA$<salt>$<hash>} string.
     *
     * @param raw the AuthMe password column
     * @return the hash for the {@code SHA-256} provider, or {@code null} if not in that format
     */
    public static @Nullable HashedPassword fromAuthMeSha256(@Nullable String raw) {
        if (raw == null || !raw.startsWith("$SHA$")) {
            return null;
        }
        var parts = raw.split("\\$");
        if (parts.length != 4 || parts[2].isEmpty() || parts[3].isEmpty()) {
            return null;
        }
        return new HashedPassword(parts[3], parts[2], "SHA-256");
    }
}
