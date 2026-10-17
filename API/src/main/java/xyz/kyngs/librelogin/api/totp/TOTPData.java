/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.totp;

import java.awt.image.BufferedImage;

/**
 * Encapsulates generated TOTP enrollment data including a visual QR code image and raw secret key.
 *
 * @param qr the rendered QR code image containing the OTPAuth URI
 * @param secret the base32 secret key string
 */
public record TOTPData(BufferedImage qr, String secret) {}
