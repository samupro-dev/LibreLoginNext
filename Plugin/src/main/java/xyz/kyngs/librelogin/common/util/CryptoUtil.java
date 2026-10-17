/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.util;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Cryptographic utility functions for memory wiping and HMAC operations. */
public final class CryptoUtil {

    private CryptoUtil() {}

    /**
     * Securely clears sensitive character data from heap memory.
     *
     * @param array the character array to overwrite with zeros
     */
    public static void wipe(char[] array) {
        if (array != null) {
            Arrays.fill(array, (char) 0);
        }
    }

    /**
     * Securely clears sensitive byte data from heap memory.
     *
     * @param array the byte array to overwrite with zeros
     */
    public static void wipe(byte[] array) {
        if (array != null) {
            Arrays.fill(array, (byte) 0);
        }
    }

    /**
     * Computes a constant-time HMAC-SHA256 hash formatted as a lower-case hexadecimal string.
     *
     * @param data the input string to hash
     * @param key the secret key used for HMAC calculation
     * @return the 64-character hexadecimal HMAC string
     */
    public static String hmacSha256(String data, String key) {
        if (data == null) return null;
        if (key == null) key = "";
        try {
            var mac = Mac.getInstance("HmacSHA256");
            var keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            var bytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            var sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            wipe(bytes);
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Failed to compute HMAC-SHA256 hash", e);
        }
    }
}
