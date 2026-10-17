/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.util;

import static xyz.kyngs.librelogin.common.config.ConfigurationKeys.MIGRATION_TYPE;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Pattern;
import org.jetbrains.annotations.Nullable;
import xyz.kyngs.librelogin.api.Logger;
import xyz.kyngs.librelogin.api.database.ReadDatabaseProvider;
import xyz.kyngs.librelogin.api.database.connector.DatabaseConnector;
import xyz.kyngs.librelogin.common.AuthenticLibreLogin;
import xyz.kyngs.librelogin.common.config.ConfigurationKeys;
import xyz.kyngs.librelogin.common.config.HoconPluginConfiguration;
import xyz.kyngs.librelogin.common.config.key.ConfigurationKey;
import xyz.kyngs.librelogin.lib.adventure.text.TextComponent;

/**
 * Miscellaneous static utility methods for UUID conversion, string generation, async task dispatch,
 * component formatting, reflection helpers, legacy-to-MiniMessage translation, and database
 * migration routines.
 */
public final class GeneralUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Pattern LEGACY_HEX_PATTERN = Pattern.compile("[&§]#([0-9a-fA-F]{6})");
    private static final Map<Character, String> LEGACY_COLOR_MAP =
            Map.ofEntries(
                    Map.entry('0', "<black>"),
                    Map.entry('1', "<dark_blue>"),
                    Map.entry('2', "<dark_green>"),
                    Map.entry('3', "<dark_aqua>"),
                    Map.entry('4', "<dark_red>"),
                    Map.entry('5', "<dark_purple>"),
                    Map.entry('6', "<gold>"),
                    Map.entry('7', "<gray>"),
                    Map.entry('8', "<dark_gray>"),
                    Map.entry('9', "<blue>"),
                    Map.entry('a', "<green>"),
                    Map.entry('b', "<aqua>"),
                    Map.entry('c', "<red>"),
                    Map.entry('d', "<light_purple>"),
                    Map.entry('e', "<yellow>"),
                    Map.entry('f', "<white>"),
                    Map.entry('k', "<obfuscated>"),
                    Map.entry('l', "<bold>"),
                    Map.entry('m', "<strikethrough>"),
                    Map.entry('n', "<underlined>"),
                    Map.entry('o', "<italic>"),
                    Map.entry('r', "<reset>"));

    private GeneralUtil() {}

    /**
     * Builds the User-Agent sent with every outbound HTTP request.
     *
     * @param version the plugin version
     * @return the User-Agent value, for example {@code LibreLogin Next v1.0.0}
     */
    public static String userAgent(String version) {
        return "LibreLogin Next v" + version;
    }

    /**
     * Reads all bytes from the provided input stream into a UTF-8 string and closes the stream.
     *
     * @param inputStream the input stream to read
     * @return the decoded string content
     * @throws IOException if an I/O error occurs
     */
    public static String readInput(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            // HttpURLConnection#getErrorStream is null when the error response has no body.
            return "";
        }
        try (inputStream) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Traverses the exception cause chain to locate the root-most causal throwable.
     *
     * @param throwable the initial throwable
     * @return the root cause, or the original throwable if no cause is attached
     */
    public static Throwable getFurthestCause(Throwable throwable) {
        while (true) {
            var cause = throwable.getCause();
            if (cause == null) return throwable;
            throwable = cause;
        }
    }

    /**
     * Parses a 32-character hexadecimal string (UUID without hyphens) into a {@link UUID}.
     *
     * @param id the undashed UUID hex string
     * @return the parsed {@link UUID}, or {@code null} if the input is null
     */
    public static UUID fromUnDashedUUID(@Nullable String id) {
        if (id == null || id.length() != 32) return null;
        try {
            long mostSig = Long.parseUnsignedLong(id.substring(0, 16), 16);
            long leastSig = Long.parseUnsignedLong(id.substring(16, 32), 16);
            return new UUID(mostSig, leastSig);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Replaces placeholder literal strings in an Adventure {@link TextComponent}.
     *
     * @param component the source component
     * @param replacements a mapping of placeholder keys to replacement values
     * @return the formatted component, or {@code null} if the input component is null
     */
    @Nullable
    public static TextComponent formatComponent(
            @Nullable TextComponent component, Map<String, String> replacements) {
        if (component == null) return null;

        for (Map.Entry<String, String> entry : replacements.entrySet()) {
            component =
                    (TextComponent)
                            component.replaceText(
                                    builder ->
                                            builder.matchLiteral(entry.getKey())
                                                    .replacement(entry.getValue()));
        }
        return component;
    }

    /**
     * Computes the offline (cracked) UUID for a given Minecraft player username.
     *
     * @param name the player username
     * @return the offline mode UUID, or {@code null} if the name is null
     */
    public static UUID getCrackedUUIDFromName(String name) {
        if (name == null) return null;
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static final String MIGRATION_MARKER = "migration.done";

    private static final Pattern EMAIL =
            Pattern.compile("^[^@\\s]{1,64}@[^@\\s.]+(\\.[^@\\s.]+)+$");

    /**
     * Performs a basic syntax check of an email address (local@domain.tld, at most 254 chars).
     *
     * @param email the address
     * @return {@code true} if it looks like an email address
     */
    public static boolean isValidEmail(String email) {
        if (email == null) return false;
        var trimmed = email.trim();
        return trimmed.length() <= 254 && EMAIL.matcher(trimmed).matches();
    }

    /**
     * Escapes text for safe insertion into HTML.
     *
     * @param value the text, may be {@code null}
     * @return the escaped text, empty for {@code null}
     */
    public static String escapeHtml(String value) {
        if (value == null) return "";
        var out = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * Checks if database migration is scheduled on startup and executes the migration procedure.
     *
     * @param configuration the plugin configuration
     * @param logger the logger instance
     * @param plugin the LibreLogin plugin instance
     */
    public static void checkAndMigrate(
            HoconPluginConfiguration configuration,
            Logger logger,
            AuthenticLibreLogin<?, ?> plugin) {
        if (configuration.get(ConfigurationKeys.MIGRATION_ON_NEXT_STARTUP)) {
            var marker = new File(plugin.getDataFolder(), MIGRATION_MARKER);
            if (marker.exists()) {
                // Re-running would re-import deleted or "forgotten" users with old credentials.
                logger.warn(
                        "Skipping migration: it already completed ({} exists). Set"
                            + " migration.on-next-startup = false, or delete {} to run it again.",
                        MIGRATION_MARKER,
                        MIGRATION_MARKER);
                return;
            }

            logger.info("Performing migration...");

            try {
                logger.info("Connecting to the OLD database...");

                ReadDatabaseProvider provider;
                DatabaseConnector<?, ?> connector = null;

                try {
                    var registration =
                            plugin.getReadProviders().get(configuration.get(MIGRATION_TYPE));
                    if (registration == null) {
                        logger.error(
                                "Migration type %s doesn't exist, please check your configuration"
                                        .formatted(configuration.get(MIGRATION_TYPE)));
                        logger.error("Aborting migration");
                        return;
                    }

                    if (registration.databaseConnector() != null) {
                        var connectorRegistration =
                                plugin.getDatabaseConnector(registration.databaseConnector());

                        if (connectorRegistration == null) {
                            logger.error(
                                    "Migration type %s is corrupted, please use a different one"
                                            .formatted(configuration.get(MIGRATION_TYPE)));
                            logger.error("Aborting migration");
                            return;
                        }

                        connector =
                                connectorRegistration
                                        .factory()
                                        .apply(
                                                "migration.old-database."
                                                        + connectorRegistration.id()
                                                        + ".");

                        connector.connect();
                    }

                    provider = registration.create(connector);

                    logger.info("Connected to the OLD database");

                } catch (Exception e) {
                    var cause = GeneralUtil.getFurthestCause(e);
                    logger.error(
                            "Failed to connect to legacy migration database (check"
                                    + " credentials/configuration). Cause: {}: {}",
                            cause.getClass().getSimpleName(),
                            cause.getMessage());
                    logger.error("Database migration aborted.");
                    return;
                }

                try {
                    logger.info("Starting data conversion... This may take a while!");
                    plugin.migrate(provider, plugin.getDatabaseProvider());
                    Files.writeString(
                            marker.toPath(),
                            configuration.get(MIGRATION_TYPE)
                                    + " "
                                    + Instant.now()
                                    + System.lineSeparator(),
                            StandardCharsets.UTF_8);
                    logger.info("Migration completed successfully, cleaning up resources.");
                    logger.warn(
                            "Set migration.on-next-startup = false in config.conf; the migration"
                                    + " will not run again while {} exists.",
                            MIGRATION_MARKER);
                } finally {
                    if (connector != null) connector.disconnect();
                }

            } catch (Exception e) {
                logger.error(
                        "An unexpected error occurred during database migration; aborting"
                                + " migration.",
                        e);
            }
        }
    }

    /**
     * Executes a runnable asynchronously on the plugin's common executor pool.
     *
     * @param runnable the runnable task
     * @return a completion stage tracking task termination
     */
    public static CompletionStage<Void> runAsync(Runnable runnable) {
        var future = new CompletableFuture<Void>();
        try {
            AuthenticLibreLogin.getExecutor()
                    .submit(
                            () -> {
                                try {
                                    runnable.run();
                                    future.complete(null);
                                } catch (Throwable e) {
                                    future.completeExceptionally(e);
                                }
                            });
        } catch (RejectedExecutionException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    /**
     * Extracts all {@link ConfigurationKey} fields declared in a class.
     *
     * @param clazz the configuration class to inspect
     * @return a list of defined configuration keys
     */
    public static List<ConfigurationKey<?>> extractKeys(Class<?> clazz) {
        var list = new ArrayList<ConfigurationKey<?>>();
        try {
            for (Field field : clazz.getFields()) {
                if (field.getType() != ConfigurationKey.class) continue;
                list.add((ConfigurationKey<?>) field.get(null));
            }
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        return list;
    }

    /**
     * Generates a cryptographically secure random alphanumeric string of the specified character
     * length.
     *
     * @param limit the desired string length
     * @return the generated alphanumeric string
     */
    public static String generateAlphanumericText(int limit) {
        var leftLimit = 48; // numeral '0'
        var rightLimit = 122; // letter 'z'

        return SECURE_RANDOM
                .ints(leftLimit, rightLimit + 1)
                .filter(i -> (i <= 57 || i >= 65) && (i <= 90 || i >= 97))
                .limit(limit)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    /**
     * Finds the first declared field in a class matching the specified type.
     *
     * @param clazz the class to reflectively inspect
     * @param fieldType the type of field to locate
     * @return the matching {@link Field}, or {@code null} if not found
     */
    public static Field getFieldByType(Class<?> clazz, Class<?> fieldType) {
        for (Field field : clazz.getDeclaredFields()) {
            if (field.getType().equals(fieldType)) {
                return field;
            }
        }
        return null;
    }

    /**
     * Translates legacy Minecraft color codes (&0-&f, §0-§f, &k-&r, §k-§r, and &#RRGGBB) into
     * native Kyori MiniMessage tags. Preserves existing MiniMessage formatting while ensuring 100%
     * retrocompatibility for legacy configuration strings.
     *
     * @param input the raw message string
     * @return the string formatted with native MiniMessage tags
     */
    public static String translateLegacyToMiniMessage(@Nullable String input) {
        if (input == null || input.isEmpty() || (!input.contains("&") && !input.contains("§"))) {
            return input;
        }

        // 1. Translate Hex colors: &#RRGGBB or §#RRGGBB -> <#RRGGBB>
        var matcher = LEGACY_HEX_PATTERN.matcher(input);
        var sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, "<#" + matcher.group(1) + ">");
        }
        matcher.appendTail(sb);
        var text = sb.toString();

        // 2. Translate Standard 16 legacy colors and formatting codes
        var result = new StringBuilder(text.length() + 16);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if ((c == '&' || c == '§') && i + 1 < text.length()) {
                char next = Character.toLowerCase(text.charAt(i + 1));
                String replacement = LEGACY_COLOR_MAP.get(next);
                if (replacement != null) {
                    result.append(replacement);
                    i++; // skip formatting char
                    continue;
                }
            }
            result.append(c);
        }
        return result.toString();
    }
}
