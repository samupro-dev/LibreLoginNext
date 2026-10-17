/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.common.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import xyz.kyngs.librelogin.api.Logger;
import xyz.kyngs.librelogin.api.util.SemanticVersion;

/**
 * Checks Modrinth for versions newer than the running one.
 *
 * <p>Uses the {@code GET /v2/project/{id|slug}/version} endpoint of the Modrinth API. Every release
 * channel (release, beta and alpha) is reported. Versions are ordered by their version number,
 * because the API does not document the order of the returned list.
 */
public final class ModrinthUpdateChecker {

    /** Modrinth project ID or slug. */
    public static final String PROJECT = "librelogin-next";

    /** Modrinth page listing every version of the project. */
    public static final String DOWNLOAD_URL =
            "https://modrinth.com/plugin/" + PROJECT + "/versions";

    private static final String VERSIONS_URL =
            "https://api.modrinth.com/v2/project/" + PROJECT + "/version?include_changelog=false";
    private static final int TIMEOUT_MILLIS = 5000;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final Gson GSON = new Gson();

    private ModrinthUpdateChecker() {}

    /**
     * A version published on Modrinth.
     *
     * @param version the parsed version number
     * @param versionNumber the version number as published
     * @param name the display name of the version
     * @param type the release channel: {@code release}, {@code beta} or {@code alpha}
     * @param published the publication time, or {@link Instant#EPOCH} if unknown
     */
    public record ModrinthVersion(
            SemanticVersion version,
            String versionNumber,
            String name,
            String type,
            Instant published) {}

    /**
     * Queries Modrinth and logs whether newer versions are available. Failures are logged as
     * warnings and never thrown.
     *
     * @param logger the plugin logger
     * @param currentVersion the version of the running plugin
     */
    public static void check(Logger logger, String currentVersion) {
        logger.info("Checking for updates...");

        try {
            var connection = (HttpURLConnection) URI.create(VERSIONS_URL).toURL().openConnection();
            connection.setRequestProperty("User-Agent", GeneralUtil.userAgent(currentVersion));
            connection.setRequestProperty("Accept", "application/json");
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);

            JsonArray root;
            try {
                int status = connection.getResponseCode();
                switch (status) {
                    case HttpURLConnection.HTTP_OK -> {}
                    case HttpURLConnection.HTTP_NOT_FOUND -> {
                        logger.warn(
                                "Failed to check for updates: Modrinth project '{}' was not found",
                                PROJECT);
                        return;
                    }
                    case HTTP_TOO_MANY_REQUESTS -> {
                        logger.warn(
                                "Failed to check for updates: Modrinth rate limit reached, retry in"
                                        + " {} seconds",
                                connection.getHeaderField("X-Ratelimit-Reset"));
                        return;
                    }
                    default -> {
                        logger.warn(
                                "Failed to check for updates: Modrinth responded with HTTP {}",
                                status);
                        return;
                    }
                }

                try (var reader =
                        new InputStreamReader(
                                connection.getInputStream(), StandardCharsets.UTF_8)) {
                    root = GSON.fromJson(reader, JsonArray.class);
                }
            } finally {
                connection.disconnect();
            }

            var current = SemanticVersion.parse(currentVersion);
            var newer = newerVersions(parse(root == null ? new JsonArray() : root), current);

            if (newer.isEmpty()) {
                logger.info(
                        "You are running the latest version of LibreLogin Next ({})",
                        currentVersion);
                return;
            }

            var latest = newer.get(newer.size() - 1);
            logger.warn(
                    "A newer version of LibreLogin Next is available! Current: {}, Latest: {}"
                            + " ({} versions behind).",
                    currentVersion,
                    latest.versionNumber(),
                    newer.size());
            for (ModrinthVersion version : newer) {
                logger.info("  - {} ({})", version.name(), version.type());
            }
            logger.info("Download updates at: {}", DOWNLOAD_URL);
        } catch (Exception e) {
            logger.warn("Failed to check for updates: {}", e.getMessage());
        }
    }

    /**
     * Converts the Modrinth version list. Entries that are not listed or whose version number
     * cannot be parsed are skipped.
     *
     * @param root the JSON array returned by Modrinth
     * @return the parsed versions, in the order of the response
     */
    static List<ModrinthVersion> parse(JsonArray root) {
        var versions = new ArrayList<ModrinthVersion>();
        for (JsonElement element : root) {
            if (!element.isJsonObject()) {
                continue;
            }
            var object = element.getAsJsonObject();

            var status = string(object, "status");
            if (status != null && !status.equals("listed")) {
                continue;
            }

            var number = string(object, "version_number");
            if (number == null) {
                continue;
            }
            var version = SemanticVersion.parse(number);
            if (version.major() == 0 && version.minor() == 0 && version.patch() == 0) {
                continue;
            }

            var name = string(object, "name");
            var type = string(object, "version_type");
            versions.add(
                    new ModrinthVersion(
                            version,
                            number,
                            name == null || name.isBlank() ? number : name,
                            type == null ? "release" : type,
                            instant(string(object, "date_published"))));
        }
        return versions;
    }

    /**
     * Selects the versions newer than the current one.
     *
     * @param versions the published versions, in any order
     * @param current the version of the running plugin
     * @return the newer versions, oldest first, so the last element is the latest version
     */
    public static List<ModrinthVersion> newerVersions(
            List<ModrinthVersion> versions, SemanticVersion current) {
        return versions.stream()
                .filter(version -> current.compare(version.version()) < 0)
                .sorted(
                        Comparator.comparing(ModrinthVersion::version)
                                .thenComparing(ModrinthVersion::published))
                .toList();
    }

    private static String string(JsonObject object, String key) {
        var value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static Instant instant(String value) {
        if (value == null) {
            return Instant.EPOCH;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return Instant.EPOCH;
        }
    }
}
