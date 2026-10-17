/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.config;

import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.ALLOWED_COMMANDS_WHILE_UNAUTHORIZED;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.AUTO_PURGE_INACTIVE_DAYS;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.DEFAULT_CRYPTO_PROVIDER;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.IP_ATTEMPTS_EXPIRE_MILLIS;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.IP_LIMIT;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.MAIL_PORT;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.MAX_IP_LOGIN_ATTEMPTS;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.MAX_LOGIN_ATTEMPTS;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.MILLISECONDS_TO_EXPIRE_LOGIN_ATTEMPTS;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.MILLISECONDS_TO_REFRESH_NOTIFICATION;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.MINIMUM_PASSWORD_LENGTH;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.MINIMUM_USERNAME_LENGTH;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.NEW_UUID_CREATOR;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.PROFILE_CONFLICT_RESOLUTION_STRATEGY;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.REDIS_DATABASE;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.REDIS_HEARTBEAT_INTERVAL_SECONDS;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.REDIS_KEY_TTL_SECONDS;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.REDIS_PORT;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.REDIS_TIMEOUT_MS;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.SECONDS_TO_AUTHORIZE;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.SESSION_TIMEOUT;
import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.TOTP_DELAY;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import xyz.kyngs.librelogin.api.crypto.CryptoProvider;
import xyz.kyngs.librelogin.common.config.key.ConfigurationKey;
import xyz.kyngs.librelogin.common.crypto.VerifyOnlyCryptoProvider;

/**
 * Validates config.conf when it is loaded or reloaded, so a bad value is reported with a clear
 * message instead of silently falling back to a default or failing later at use time.
 */
public final class ConfigurationValidator {

    private ConfigurationValidator() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Validates a loaded configuration.
     *
     * @param helper the configuration to check
     * @param cryptoProviders looks up a registered crypto provider by identifier
     * @return a list of problems, empty when the configuration is valid
     */
    public static List<String> validate(
            ConfigurateHelper helper, Function<String, CryptoProvider> cryptoProviders) {
        var problems = new ArrayList<String>();

        var crypto = read(helper, DEFAULT_CRYPTO_PROVIDER, problems);
        if (crypto != null) {
            var provider = cryptoProviders.apply(crypto);
            if (provider == null) {
                problems.add(
                        DEFAULT_CRYPTO_PROVIDER.key() + ": unknown algorithm '" + crypto + "'");
            } else if (provider instanceof VerifyOnlyCryptoProvider) {
                problems.add(
                        DEFAULT_CRYPTO_PROVIDER.key()
                                + ": '"
                                + crypto
                                + "' can only verify imported passwords; use Argon-2ID, BCrypt-2A,"
                                + " SCrypt or PBKDF2");
            }
        }

        var uuidCreator = read(helper, NEW_UUID_CREATOR, problems);
        if (uuidCreator != null && !uuidCreator.isBlank()) {
            try {
                NewUUIDCreator.valueOf(uuidCreator.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                problems.add(NEW_UUID_CREATOR.key() + ": must be CRACKED, MOJANG or RANDOM");
            }
        }

        // The getter itself parses the enum; a bad value throws here.
        read(helper, PROFILE_CONFLICT_RESOLUTION_STRATEGY, problems);

        var allowed = read(helper, ALLOWED_COMMANDS_WHILE_UNAUTHORIZED, problems);
        if (allowed != null && allowed.isEmpty()) {
            problems.add(
                    ALLOWED_COMMANDS_WHILE_UNAUTHORIZED.key()
                            + ": must not be empty, players could not run /login or /register");
        }

        atLeast(helper, SECONDS_TO_AUTHORIZE, 0, problems);
        minusOneOrAtLeast(helper, MAX_LOGIN_ATTEMPTS, 1, problems);
        atLeast(helper, MILLISECONDS_TO_EXPIRE_LOGIN_ATTEMPTS, 1, problems);
        atLeast(helper, MINIMUM_PASSWORD_LENGTH, 0, problems);
        between(helper, MINIMUM_USERNAME_LENGTH, 1, 16, problems);
        minusOneOrAtLeast(helper, IP_LIMIT, 0, problems);
        var refresh = read(helper, MILLISECONDS_TO_REFRESH_NOTIFICATION, problems);
        if (refresh != null && refresh > 0 && refresh < 50) {
            // 0 or less disables reminders; below one tick (50 ms) it would spam every tick.
            problems.add(
                    MILLISECONDS_TO_REFRESH_NOTIFICATION.key()
                            + ": must be 0 (disabled) or at least 50 (found "
                            + refresh
                            + ")");
        }
        atLeastLong(helper, SESSION_TIMEOUT, 0, problems);
        atLeast(helper, MAX_IP_LOGIN_ATTEMPTS, 0, problems);
        atLeastLong(helper, IP_ATTEMPTS_EXPIRE_MILLIS, 1, problems);
        atLeast(helper, AUTO_PURGE_INACTIVE_DAYS, 0, problems);
        atLeast(helper, TOTP_DELAY, 0, problems);
        between(helper, MAIL_PORT, 1, 65535, problems);
        between(helper, REDIS_PORT, 1, 65535, problems);
        atLeast(helper, REDIS_DATABASE, 0, problems);
        atLeast(helper, REDIS_TIMEOUT_MS, 1, problems);
        atLeast(helper, REDIS_HEARTBEAT_INTERVAL_SECONDS, 1, problems);

        var heartbeat = read(helper, REDIS_HEARTBEAT_INTERVAL_SECONDS, problems);
        var ttl = read(helper, REDIS_KEY_TTL_SECONDS, problems);
        if (heartbeat != null && ttl != null && ttl <= heartbeat) {
            problems.add(
                    REDIS_KEY_TTL_SECONDS.key()
                            + ": must be greater than "
                            + REDIS_HEARTBEAT_INTERVAL_SECONDS.key()
                            + " or presence keys expire between heartbeats");
        }

        return problems;
    }

    private static <T> T read(
            ConfigurateHelper helper, ConfigurationKey<T> key, List<String> problems) {
        try {
            return helper.get(key);
        } catch (RuntimeException e) {
            problems.add(key.key() + ": invalid value (" + e.getMessage() + ")");
            return null;
        }
    }

    private static void atLeast(
            ConfigurateHelper helper,
            ConfigurationKey<Integer> key,
            int min,
            List<String> problems) {
        var value = read(helper, key, problems);
        if (value != null && value < min) {
            problems.add(key.key() + ": must be at least " + min + " (found " + value + ")");
        }
    }

    private static void atLeastLong(
            ConfigurateHelper helper, ConfigurationKey<Long> key, long min, List<String> problems) {
        var value = read(helper, key, problems);
        if (value != null && value < min) {
            problems.add(key.key() + ": must be at least " + min + " (found " + value + ")");
        }
    }

    private static void minusOneOrAtLeast(
            ConfigurateHelper helper,
            ConfigurationKey<Integer> key,
            int min,
            List<String> problems) {
        var value = read(helper, key, problems);
        if (value != null && value != -1 && value < min) {
            problems.add(
                    key.key()
                            + ": must be -1 (disabled) or at least "
                            + min
                            + " (found "
                            + value
                            + ")");
        }
    }

    private static void between(
            ConfigurateHelper helper,
            ConfigurationKey<Integer> key,
            int min,
            int max,
            List<String> problems) {
        var value = read(helper, key, problems);
        if (value != null && (value < min || value > max)) {
            problems.add(
                    key.key()
                            + ": must be between "
                            + min
                            + " and "
                            + max
                            + " (found "
                            + value
                            + ")");
        }
    }
}
