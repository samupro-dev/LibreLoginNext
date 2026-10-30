package xyz.kyngs.librelogin.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import xyz.kyngs.librelogin.api.util.SemanticVersion;
import xyz.kyngs.librelogin.common.util.GitHubUpdateChecker.GitHubRelease;

class GitHubUpdateCheckerTest {

    private static GitHubRelease release(String tag, boolean prerelease, String published) {
        return new GitHubRelease(
                SemanticVersion.parse(tag), tag, tag, prerelease, Instant.parse(published));
    }

    @Test
    void reportsNewerReleasesAndPreReleasesOldestFirst() {
        var releases =
                List.of(
                        release("v0.27.0-beta.1", true, "2027-03-01T00:00:00Z"),
                        release("v0.26.0", false, "2026-10-01T00:00:00Z"),
                        release("v0.26.2", false, "2027-01-01T00:00:00Z"),
                        release("v0.26.1", false, "2026-10-30T00:00:00Z"));

        var newer = GitHubUpdateChecker.newerReleases(releases, SemanticVersion.parse("0.26.1"));

        assertEquals(
                List.of("v0.26.2", "v0.27.0-beta.1"),
                newer.stream().map(GitHubRelease::tag).toList());
        assertTrue(newer.get(newer.size() - 1).prerelease());
    }

    @Test
    void ignoresTheRunningAndOlderReleases() {
        var releases =
                List.of(
                        release("0.26.1", false, "2026-10-30T00:00:00Z"),
                        release("0.25.11", false, "2026-10-15T00:00:00Z"));

        assertTrue(
                GitHubUpdateChecker.newerReleases(releases, SemanticVersion.parse("0.26.1"))
                        .isEmpty());
    }

    @Test
    void ordersEqualVersionNumbersByPublicationTime() {
        var releases =
                List.of(
                        release("0.27.0", false, "2027-01-10T00:00:00Z"),
                        release("0.27.0-beta.2", true, "2027-01-05T00:00:00Z"),
                        release("0.27.0-beta.1", true, "2027-01-01T00:00:00Z"));

        var newer = GitHubUpdateChecker.newerReleases(releases, SemanticVersion.parse("0.26.1"));

        assertEquals(
                List.of("0.27.0-beta.1", "0.27.0-beta.2", "0.27.0"),
                newer.stream().map(GitHubRelease::tag).toList());
    }

    @Test
    void developmentBuildIsOlderThanTheMatchingRelease() {
        var releases = List.of(release("0.26.1", false, "2026-10-30T00:00:00Z"));

        assertEquals(
                1,
                GitHubUpdateChecker.newerReleases(
                                releases, SemanticVersion.parse("0.26.1-SNAPSHOT"))
                        .size());
    }
}
