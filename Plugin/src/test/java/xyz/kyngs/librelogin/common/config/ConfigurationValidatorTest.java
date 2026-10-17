/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.spongepowered.configurate.CommentedConfigurationNode;
import xyz.kyngs.librelogin.api.crypto.CryptoProvider;
import xyz.kyngs.librelogin.common.crypto.BCrypt2ACryptoProvider;
import xyz.kyngs.librelogin.common.crypto.LegacyMessageDigestCryptoProvider;

class ConfigurationValidatorTest {

    private final Map<String, CryptoProvider> providers =
            Map.of(
                    "Argon-2ID", new BCrypt2ACryptoProvider(),
                    "BCrypt-2A", new BCrypt2ACryptoProvider(),
                    "SHA-256", new LegacyMessageDigestCryptoProvider("SHA-256", "SHA-256", false));

    private List<String> validate(Map<String, Object> values) {
        var helper = new ConfigurateHelper(CommentedConfigurationNode.root());
        values.forEach(helper::set);
        return ConfigurationValidator.validate(helper, providers::get);
    }

    @Test
    void defaultsAreValid() {
        assertEquals(List.of(), validate(Map.of()));
    }

    @Test
    void reportsEveryBadValue() {
        var problems =
                validate(
                        Map.of(
                                "max-login-attempts", -5,
                                "milliseconds-to-refresh-login-attempts", -1,
                                "milliseconds-to-refresh-notification", 10,
                                "session-timeout", -1L,
                                "new-uuid-creator", "SOMETHING",
                                "allowed-commands-while-unauthorized", List.of(),
                                "redis.key-ttl-seconds", 5));
        assertEquals(7, problems.size(), String.join("\n", problems));
    }

    @Test
    void rejectsVerifyOnlyAndUnknownDefaultProviders() {
        assertTrue(
                validate(Map.of("default-crypto-provider", "SHA-256")).get(0).contains("verify"));
        assertTrue(validate(Map.of("default-crypto-provider", "MD5")).get(0).contains("unknown"));
    }

    @Test
    void acceptsDisabledValues() {
        assertEquals(
                List.of(),
                validate(
                        Map.of(
                                "max-login-attempts", -1,
                                "milliseconds-to-refresh-notification", 0,
                                "ip-limit", -1,
                                "session-timeout", 0L)));
    }

    @Test
    void enumParsingIgnoresTurkishLocale() {
        var previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals(
                    List.of(),
                    validate(
                            Map.of(
                                    "new-uuid-creator", "random",
                                    "profile-conflict-resolution-strategy", "use_offline")));
        } finally {
            Locale.setDefault(previous);
        }
    }
}
