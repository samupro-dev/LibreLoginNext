/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.totp;

import xyz.kyngs.librelogin.api.database.User;

/**
 * Service provider interface for generating TOTP secrets, QR code visualizations, and validating
 * codes.
 */
public interface TOTPProvider {

    /**
     * Generates a new random secret and corresponding QR code payload for a user.
     *
     * @param user the user domain model
     * @return the generated {@link TOTPData}
     */
    TOTPData generate(User user);

    /**
     * Generates TOTP data and QR code visualization using an existing secret key.
     *
     * @param user the user domain model
     * @param secret the base32 secret key string
     * @return the generated {@link TOTPData}
     */
    TOTPData generate(User user, String secret);

    /**
     * Validates a candidate numeric verification code against a base32 TOTP secret key.
     *
     * @param code the numeric code entered by the user
     * @param secret the base32 secret key
     * @return true if the code is valid within the acceptable time window, false otherwise
     */
    boolean verify(Integer code, String secret);
}
