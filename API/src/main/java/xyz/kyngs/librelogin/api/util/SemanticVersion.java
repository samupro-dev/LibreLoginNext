/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.kyngs.librelogin.api.util;

/**
 * Representation of a semantic version (major.minor.patch[-SNAPSHOT]).
 *
 * @param major the major version number
 * @param minor the minor version number
 * @param patch the patch version number
 * @param dev whether this is a development (SNAPSHOT) build
 */
public record SemanticVersion(int major, int minor, int patch, boolean dev)
        implements Comparable<SemanticVersion> {

    /**
     * Parses a semantic version string in the format {@code [v]major.minor.patch[-SNAPSHOT]}.
     *
     * @param version the version string to parse
     * @return the parsed {@link SemanticVersion} instance
     */
    public static SemanticVersion parse(String version) {
        if (version == null || version.isBlank()) {
            return new SemanticVersion(0, 0, 0, false);
        }
        var clean = version.trim();
        if (clean.startsWith("v") || clean.startsWith("V")) {
            clean = clean.substring(1);
        }
        var isDev =
                clean.toUpperCase(java.util.Locale.ROOT).contains("SNAPSHOT")
                        || clean.toUpperCase(java.util.Locale.ROOT).contains("DEV");
        var baseVersion = clean.split("-")[0];
        var split = baseVersion.split("\\.");
        var major = split.length > 0 ? parseSafeInt(split[0]) : 0;
        var minor = split.length > 1 ? parseSafeInt(split[1]) : 0;
        var patch = split.length > 2 ? parseSafeInt(split[2]) : 0;
        return new SemanticVersion(major, minor, patch, isDev);
    }

    private static int parseSafeInt(String segment) {
        if (segment == null || segment.isBlank()) return 0;
        var digits = segment.replaceAll("\\D+", "");
        if (digits.isEmpty()) return 0;
        try {
            return Integer.parseInt(digits);
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE; // more digits than an int holds
        }
    }

    @Override
    public int compareTo(SemanticVersion other) {
        return compare(other);
    }

    /**
     * Compares this semantic version with another semantic version.
     *
     * @param other the version to compare against
     * @return negative if this is older, 0 if equal, positive if this is newer
     */
    public int compare(SemanticVersion other) {
        if (major != other.major) {
            return major < other.major ? -1 : 1;
        }
        if (minor != other.minor) {
            return minor < other.minor ? -1 : 1;
        }
        if (patch != other.patch) {
            return patch < other.patch ? -1 : 1;
        }
        return dev ? (other.dev ? 0 : -1) : (other.dev ? 1 : 0);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch + (dev ? "-SNAPSHOT" : "");
    }
}
