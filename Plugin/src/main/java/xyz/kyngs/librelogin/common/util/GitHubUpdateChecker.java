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
 * Checks the GitHub releases of the project for versions newer than the running one.
 *
 * <p>Uses the {@code GET /repos/{owner}/{repo}/releases} endpoint of the GitHub REST API. Drafts
 * are ignored, pre-releases are reported. Releases are ordered by their version number, because the
 * API orders them by creation date.
 */
public final class GitHubUpdateChecker {

    /** GitHub repository in the {@code owner/name} form. */
    public static final String REPOSITORY = "samupro-dev/LibreLoginNext";

    /** SpigotMC page where the plugin is downloaded. */
    public static final String DOWNLOAD_URL = "https://www.spigotmc.org/resources/139932/";

    private static final String RELEASES_URL =
            "https://api.github.com/repos/" + REPOSITORY + "/releases?per_page=100";
    // Latest REST API version; without the header GitHub falls back to 2022-11-28.
    private static final String API_VERSION = "2026-03-10";
    private static final int TIMEOUT_MILLIS = 5000;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final Gson GSON = new Gson();

    private GitHubUpdateChecker() {}

    /**
     * A release published on GitHub.
     *
     * @param version the parsed version number
     * @param tag the release tag as published
     * @param name the display name of the release
     * @param prerelease whether the release is marked as a pre-release
     * @param published the publication time, or {@link Instant#EPOCH} if unknown
     */
    public record GitHubRelease(
            SemanticVersion version,
            String tag,
            String name,
            boolean prerelease,
            Instant published) {}

    /**
     * Queries GitHub and logs whether newer releases are available. Failures are logged as warnings
     * and never thrown.
     *
     * @param logger the plugin logger
     * @param currentVersion the version of the running plugin
     */
    public static void check(Logger logger, String currentVersion) {
        logger.info("Checking for updates...");

        try {
            var connection = (HttpURLConnection) URI.create(RELEASES_URL).toURL().openConnection();
            connection.setRequestProperty("User-Agent", GeneralUtil.userAgent(currentVersion));
            connection.setRequestProperty("Accept", "application/vnd.github+json");
            connection.setRequestProperty("X-GitHub-Api-Version", API_VERSION);
            connection.setConnectTimeout(TIMEOUT_MILLIS);
            connection.setReadTimeout(TIMEOUT_MILLIS);

            JsonArray root;
            try {
                int status = connection.getResponseCode();
                // GitHub answers 403 or 429 with x-ratelimit-remaining: 0 when the hourly limit
                // for this IP is exhausted; any other 403 is a different refusal.
                boolean rateLimited =
                        status == HTTP_TOO_MANY_REQUESTS
                                || (status == HttpURLConnection.HTTP_FORBIDDEN
                                        && "0"
                                                .equals(
                                                        connection.getHeaderField(
                                                                "X-RateLimit-Remaining")));
                if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                    logger.warn(
                            "Failed to check for updates: GitHub repository '{}' was not found",
                            REPOSITORY);
                    return;
                }
                if (rateLimited) {
                    logger.warn(
                            "Failed to check for updates: GitHub rate limit reached (HTTP {}),"
                                    + " resets at epoch second {}",
                            status,
                            connection.getHeaderField("X-RateLimit-Reset"));
                    return;
                }
                if (status != HttpURLConnection.HTTP_OK) {
                    logger.warn(
                            "Failed to check for updates: GitHub responded with HTTP {}", status);
                    return;
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
            var newer = newerReleases(parse(root == null ? new JsonArray() : root), current);

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
                    latest.tag(),
                    newer.size());
            for (GitHubRelease release : newer) {
                logger.info(
                        "  - {}{}", release.name(), release.prerelease() ? " (pre-release)" : "");
            }
            logger.info("Download updates at: {}", DOWNLOAD_URL);
        } catch (Exception e) {
            logger.warn("Failed to check for updates: {}", e.getMessage());
        }
    }

    /**
     * Converts the GitHub release list. Drafts and releases whose tag cannot be parsed as a version
     * are skipped.
     *
     * @param root the JSON array returned by GitHub
     * @return the parsed releases, in the order of the response
     */
    static List<GitHubRelease> parse(JsonArray root) {
        var releases = new ArrayList<GitHubRelease>();
        for (JsonElement element : root) {
            if (!element.isJsonObject()) {
                continue;
            }
            var object = element.getAsJsonObject();
            if (bool(object, "draft")) {
                continue;
            }

            var tag = string(object, "tag_name");
            if (tag == null) {
                continue;
            }
            var version = SemanticVersion.parse(tag);
            if (version.major() == 0 && version.minor() == 0 && version.patch() == 0) {
                continue;
            }

            var name = string(object, "name");
            releases.add(
                    new GitHubRelease(
                            version,
                            tag,
                            name == null || name.isBlank() ? tag : name,
                            bool(object, "prerelease"),
                            instant(string(object, "published_at"))));
        }
        return releases;
    }

    /**
     * Selects the releases newer than the current version.
     *
     * @param releases the published releases, in any order
     * @param current the version of the running plugin
     * @return the newer releases, oldest first, so the last element is the latest release
     */
    public static List<GitHubRelease> newerReleases(
            List<GitHubRelease> releases, SemanticVersion current) {
        return releases.stream()
                .filter(release -> current.compare(release.version()) < 0)
                .sorted(
                        Comparator.comparing(GitHubRelease::version)
                                .thenComparing(GitHubRelease::published))
                .toList();
    }

    private static String string(JsonObject object, String key) {
        var value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static boolean bool(JsonObject object, String key) {
        var value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsBoolean();
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
