package xyz.kyngs.librelogin.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import xyz.kyngs.librelogin.api.util.SemanticVersion;
import xyz.kyngs.librelogin.common.util.ModrinthUpdateChecker.ModrinthVersion;

class ModrinthUpdateCheckerTest {

    private static ModrinthVersion version(String number, String type, String published) {
        return new ModrinthVersion(
                SemanticVersion.parse(number), number, number, type, Instant.parse(published));
    }

    @Test
    void reportsNewerVersionsOfEveryChannelOldestFirst() {
        var versions =
                List.of(
                        version("0.27.0-alpha.1", "alpha", "2026-03-01T00:00:00Z"),
                        version("0.25.10", "release", "2025-10-01T00:00:00Z"),
                        version("0.26.0", "release", "2026-01-01T00:00:00Z"),
                        version("0.25.12-beta.1", "beta", "2025-11-01T00:00:00Z"));

        var newer = ModrinthUpdateChecker.newerVersions(versions, SemanticVersion.parse("0.25.11"));

        assertEquals(
                List.of("0.25.12-beta.1", "0.26.0", "0.27.0-alpha.1"),
                newer.stream().map(ModrinthVersion::versionNumber).toList());
        assertEquals("alpha", newer.get(newer.size() - 1).type());
    }

    @Test
    void ignoresTheRunningAndOlderVersions() {
        var versions =
                List.of(
                        version("0.25.11", "release", "2025-10-15T00:00:00Z"),
                        version("0.24.0", "release", "2025-06-01T00:00:00Z"));

        assertTrue(
                ModrinthUpdateChecker.newerVersions(versions, SemanticVersion.parse("0.25.11"))
                        .isEmpty());
    }

    @Test
    void ordersEqualVersionNumbersByPublicationTime() {
        var versions =
                List.of(
                        version("0.26.0", "release", "2026-01-10T00:00:00Z"),
                        version("0.26.0-beta.2", "beta", "2026-01-05T00:00:00Z"),
                        version("0.26.0-beta.1", "beta", "2026-01-01T00:00:00Z"));

        var newer = ModrinthUpdateChecker.newerVersions(versions, SemanticVersion.parse("0.25.11"));

        assertEquals(
                List.of("0.26.0-beta.1", "0.26.0-beta.2", "0.26.0"),
                newer.stream().map(ModrinthVersion::versionNumber).toList());
    }

    @Test
    void developmentBuildIsOlderThanTheMatchingRelease() {
        var versions = List.of(version("0.25.11", "release", "2025-10-15T00:00:00Z"));

        assertEquals(
                1,
                ModrinthUpdateChecker.newerVersions(
                                versions, SemanticVersion.parse("0.25.11-SNAPSHOT"))
                        .size());
    }
}
