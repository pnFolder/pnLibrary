package org.pnfolder.pnlibrary.api.version;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Platform-independent semantic version.
 *
 * <p>Implements SemVer 2.0.0 precedence for the numeric core and arbitrary prerelease identifiers.
 * Numeric identifiers are compared numerically, so {@code beta.10} is newer than {@code beta.2}. The word {@code dev}
 * is the one deviation from plain SemVer: it ranks below every other word, so channels order
 * {@code dev < alpha < beta < rc < stable}. Build metadata is validated but omitted from this value because it does
 * not affect precedence.
 *
 * <pre>{@code
 * SemanticVersion.parse("2.0.0").isAtLeast("1.9.0");
 * }</pre>
 */
public final class SemanticVersion implements Comparable<SemanticVersion> {
    private static final Pattern FORMAT = Pattern.compile(
        "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
            + "(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?"
            + "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$");

    private final int major;
    private final int minor;
    private final int patch;
    private final List<String> prerelease;

    private SemanticVersion(int major, int minor, int patch, List<String> prerelease) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.prerelease = Collections.unmodifiableList(new ArrayList<>(prerelease));
    }

    /** The major compatibility version. */
    public int getMajor() {
        return major;
    }

    /** The minor feature version. */
    public int getMinor() {
        return minor;
    }

    /** The patch correction version. */
    public int getPatch() {
        return patch;
    }

    /** The immutable ordered prerelease identifiers, empty for a stable release. */
    public @NotNull List<String> getPrerelease() {
        return prerelease;
    }

    /** Returns {@code true} when this version is not older than {@code minimum}. */
    public boolean isAtLeast(@NotNull SemanticVersion minimum) {
        return compareTo(minimum) >= 0;
    }

    /** Convenience overload accepting a version string. */
    public boolean isAtLeast(@NotNull String minimum) {
        return isAtLeast(parse(minimum));
    }

    /** Returns whether this version belongs to {@code range}. */
    public boolean isIn(@NotNull VersionRange range) {
        return range.contains(this);
    }

    /** Compares precedence according to SemVer 2.0.0, excluding build metadata. */
    @Override
    public int compareTo(@NotNull SemanticVersion other) {
        int core = Integer.compare(major, other.major);
        if (core == 0) {
            core = Integer.compare(minor, other.minor);
        }
        if (core == 0) {
            core = Integer.compare(patch, other.patch);
        }
        if (core != 0) {
            return core;
        }
        return comparePrerelease(other);
    }

    private int comparePrerelease(SemanticVersion other) {
        if (prerelease.isEmpty()) {
            return other.prerelease.isEmpty() ? 0 : 1;
        }
        if (other.prerelease.isEmpty()) {
            return -1;
        }
        int length = Math.max(prerelease.size(), other.prerelease.size());
        for (int index = 0; index < length; index++) {
            if (index >= prerelease.size()) {
                return -1;
            }
            if (index >= other.prerelease.size()) {
                return 1;
            }
            int result = compareIdentifier(prerelease.get(index), other.prerelease.get(index));
            if (result != 0) {
                return result;
            }
        }
        return 0;
    }

    private static int compareIdentifier(String left, String right) {
        boolean leftNumber = isNumber(left);
        boolean rightNumber = isNumber(right);
        if (leftNumber && rightNumber) {
            // Numeric identifiers have no leading zeros, so length defines magnitude.
            int byLength = Integer.compare(left.length(), right.length());
            return byLength != 0 ? byLength : left.compareTo(right);
        }
        if (leftNumber) {
            return -1;
        }
        if (rightNumber) {
            return 1;
        }
        return rankWord(left).compareTo(rankWord(right));
    }

    private static boolean isNumber(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isDigit(value.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    // `dev` is the least mature channel, but alphabetically it would sit between `beta` and `rc`.
    private static String rankWord(String word) {
        return "dev".equals(word) ? "" : word;
    }

    /** Returns whether two versions have equal SemVer precedence. */
    @Override
    public boolean equals(Object other) {
        return other instanceof SemanticVersion && compareTo((SemanticVersion) other) == 0;
    }

    /** Hashes exactly the fields that participate in {@link #equals(Object)}. */
    @Override
    public int hashCode() {
        return 31 * (31 * (31 * major + minor) + patch) + prerelease.hashCode();
    }

    /** Returns the normalized version with prerelease identifiers; build metadata is omitted. */
    @Override
    public String toString() {
        StringBuilder text = new StringBuilder().append(major).append('.').append(minor).append('.').append(patch);
        for (int index = 0; index < prerelease.size(); index++) {
            text.append(index == 0 ? '-' : '.').append(prerelease.get(index));
        }
        return text.toString();
    }

    /** Parses SemVer, allowing a leading {@code v} and build metadata. */
    public static @NotNull SemanticVersion parse(@NotNull String raw) {
        SemanticVersion version = tryParse(raw);
        if (version == null) {
            throw new IllegalArgumentException("Invalid semantic version: " + raw);
        }
        return version;
    }

    /** Returns a parsed version, or {@code null} for invalid input. */
    public static @Nullable SemanticVersion tryParse(@NotNull String raw) {
        String value = raw.trim();
        if (value.startsWith("v")) {
            value = value.substring(1);
        }
        Matcher match = FORMAT.matcher(value);
        if (!match.matches()) {
            return null;
        }
        List<String> prerelease = prerelease(match.group(4));
        for (String identifier : prerelease) {
            if (identifier.length() > 1 && isNumber(identifier) && identifier.startsWith("0")) {
                return null;
            }
        }
        try {
            return new SemanticVersion(Integer.parseInt(match.group(1)), Integer.parseInt(match.group(2)),
                Integer.parseInt(match.group(3)), prerelease);
        } catch (NumberFormatException tooLarge) {
            return null;
        }
    }

    private static List<String> prerelease(@Nullable String text) {
        List<String> identifiers = new ArrayList<>();
        if (text == null) {
            return identifiers;
        }
        for (String identifier : text.split("\\.", -1)) {
            if (!identifier.trim().isEmpty()) {
                identifiers.add(identifier);
            }
        }
        return identifiers;
    }
}
