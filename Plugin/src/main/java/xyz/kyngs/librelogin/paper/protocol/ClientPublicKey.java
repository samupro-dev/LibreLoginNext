/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.paper.protocol;

import com.github.retrooper.packetevents.util.crypto.SignatureData;
import java.security.PublicKey;
import java.time.Instant;

/**
 * Record representing a Minecraft client's public encryption key profile and expiration timestamp.
 *
 * @param expire the expiration timestamp of the public key
 * @param key the RSA public key
 * @param signature the Mojang signature validating the key
 */
public record ClientPublicKey(Instant expire, PublicKey key, byte[] signature) {

    /**
     * Checks whether the client public key has expired relative to the given timestamp.
     *
     * @param timestamp the current instant to check against
     * @return {@code true} if the key is expired; {@code false} otherwise
     */
    public boolean expired(Instant timestamp) {
        return !timestamp.isBefore(expire);
    }

    /**
     * Converts this client key into a PacketEvents {@link SignatureData} instance.
     *
     * @return the corresponding {@link SignatureData}
     */
    public SignatureData toSignatureData() {
        return new SignatureData(expire, key, signature);
    }
}
