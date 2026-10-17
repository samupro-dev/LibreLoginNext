/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.totp;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.helger.totp.code.DefaultCodeGenerator;
import com.helger.totp.code.DefaultCodeVerifier;
import com.helger.totp.qr.QrData;
import com.helger.totp.secret.DefaultSecretGenerator;
import com.helger.totp.secret.ISecretGenerator;
import com.helger.totp.time.SystemTimeProvider;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import xyz.kyngs.librelogin.api.database.User;
import xyz.kyngs.librelogin.api.totp.TOTPData;
import xyz.kyngs.librelogin.api.totp.TOTPProvider;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;

/**
 * Standard implementation of {@link TOTPProvider} compliant with RFC 6238 (Time-Based One-Time
 * Password Algorithm) utilizing {@code ph-totp} and ZXing for direct QR code rendering.
 *
 * <p>This provider generates Base32-encoded shared secrets, renders 128x128 QR code images for
 * authenticator application pairing, and validates 6-digit one-time passcodes against the system
 * time with standard time-step tolerance and anti-replay protection.
 *
 * <p>This class is thread-safe and can be used concurrently across multiple authentication
 * sessions.
 */
public class AuthenticTOTPProvider implements TOTPProvider {

    private static final Map<EncodeHintType, Object> QR_HINTS =
            Map.of(
                    EncodeHintType.MARGIN,
                    1,
                    EncodeHintType.ERROR_CORRECTION,
                    ErrorCorrectionLevel.M,
                    EncodeHintType.CHARACTER_SET,
                    StandardCharsets.UTF_8.name());
    private static final QRCodeWriter QR_WRITER = new QRCodeWriter();

    private final AuthenticLibreLogin<?, ?> plugin;
    private final ISecretGenerator secretGenerator;
    private final DefaultCodeVerifier verifier;
    private final Cache<String, TOTPData> qrCache;
    private final Cache<String, Long> usedTimesteps;

    /**
     * Constructs a new {@code AuthenticTOTPProvider} with the given plugin instance.
     *
     * @param plugin the plugin instance for accessing configuration
     */
    public AuthenticTOTPProvider(AuthenticLibreLogin<?, ?> plugin) {
        this.plugin = plugin;
        this.secretGenerator = new DefaultSecretGenerator();
        this.verifier =
                new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
        this.qrCache =
                Caffeine.newBuilder()
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .maximumSize(5_000)
                        .build();
        this.usedTimesteps =
                Caffeine.newBuilder()
                        .expireAfterWrite(90, TimeUnit.SECONDS)
                        .maximumSize(10_000)
                        .build();
    }

    @Override
    public TOTPData generate(User user) {
        return generate(user, secretGenerator.generate());
    }

    @Override
    public TOTPData generate(User user, String secret) {
        return qrCache.get(
                secret,
                sec -> {
                    var data =
                            new QrData.Builder()
                                    .label(user.getLastNickname())
                                    .issuer(
                                            plugin.getConfiguration()
                                                    .get(ConfigurationKeys.TOTP_LABEL))
                                    .secret(sec)
                                    .build();

                    try {
                        BitMatrix bitMatrix =
                                QR_WRITER.encode(
                                        data.getUri(), BarcodeFormat.QR_CODE, 128, 128, QR_HINTS);
                        BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix);
                        return new TOTPData(image, sec);
                    } catch (Exception e) {
                        throw new RuntimeException(
                                "Failed to generate QR code for user " + user.getLastNickname(), e);
                    }
                });
    }

    @Override
    public boolean verify(Integer code, String secret) {
        if (code == null || code < 0 || code > 999_999 || secret == null) return false;
        // Replay protection keyed on the time slot the code actually matched (the verifier accepts
        // +/-1 slot): a code is accepted once, and only codes from a later slot after it.
        Long slot = verifier.getMatchingTimeSlot(secret, padCode(code));
        if (slot == null) return false;
        var accepted = new boolean[1];
        usedTimesteps
                .asMap()
                .compute(
                        secret,
                        (key, previous) -> {
                            if (previous == null || slot > previous) {
                                accepted[0] = true;
                                return slot;
                            }
                            return previous;
                        });
        return accepted[0];
    }

    private static String padCode(int code) {
        if (code >= 100_000) return Integer.toString(code);
        if (code >= 10_000) return "0" + code;
        if (code >= 1_000) return "00" + code;
        if (code >= 100) return "000" + code;
        if (code >= 10) return "0000" + code;
        return "00000" + code;
    }
}
