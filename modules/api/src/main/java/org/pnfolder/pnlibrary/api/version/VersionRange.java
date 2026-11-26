package org.pnfolder.pnlibrary.api.version;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Semantic version range with inclusive or exclusive boundaries.
 *
 * <pre>{@code
 * VersionRange.closed("2.0.0", "3.0.0").contains(version);
 * }</pre>
 */
public final class VersionRange {
    private final SemanticVersion minimum;
    private final SemanticVersion maximum;
    private final boolean includeMinimum;
    private final boolean includeMaximum;

    private VersionRange(@Nullable SemanticVersion minimum, @Nullable SemanticVersion maximum,
                         boolean includeMinimum, boolean includeMaximum) {
        if (minimum != null && maximum != null && minimum.compareTo(maximum) > 0) {
            throw new IllegalArgumentException("Minimum version must not be newer than maximum version");
        }
        this.minimum = minimum;
        this.maximum = maximum;
        this.includeMinimum = includeMinimum;
        this.includeMaximum = includeMaximum;
    }

    /** The lower boundary, or {@code null} when unbounded below. */
    public @Nullable SemanticVersion getMinimum() {
        return minimum;
    }

    /** The upper boundary, or {@code null} when unbounded above. */
    public @Nullable SemanticVersion getMaximum() {
        return maximum;
    }

    /** Whether the minimum itself is included. Ignored when no minimum exists. */
    public boolean isIncludeMinimum() {
        return includeMinimum;
    }

    /** Whether the maximum itself is included. Ignored when no maximum exists. */
    public boolean isIncludeMaximum() {
        return includeMaximum;
    }

    /** Returns whether {@code version} belongs to this range. */
    public boolean contains(@NotNull SemanticVersion version) {
        boolean afterMinimum = minimum == null
            || (includeMinimum ? version.compareTo(minimum) >= 0 : version.compareTo(minimum) > 0);
        boolean beforeMaximum = maximum == null
            || (includeMaximum ? version.compareTo(maximum) <= 0 : version.compareTo(maximum) < 0);
        return afterMinimum && beforeMaximum;
    }

    /** Parses and checks a version string. */
    public boolean contains(@NotNull String version) {
        return contains(SemanticVersion.parse(version));
    }

    /** Interval notation such as {@code [2.0.0, 3.0.0)}; a missing boundary is shown as {@code *}. */
    @Override
    public String toString() {
        return (includeMinimum ? "[" : "(") + (minimum == null ? "*" : minimum) + ", "
            + (maximum == null ? "*" : maximum) + (includeMaximum ? "]" : ")");
    }

    /** Returns a range that accepts every version. */
    public static @NotNull VersionRange any() {
        return new VersionRange(null, null, true, true);
    }

    /** Returns a range accepting versions from {@code minimum}, inclusive. */
    public static @NotNull VersionRange atLeast(@NotNull String minimum) {
        return new VersionRange(SemanticVersion.parse(minimum), null, true, true);
    }

    /** Returns a range accepting versions above {@code minimum}, exclusive. */
    public static @NotNull VersionRange greaterThan(@NotNull String minimum) {
        return new VersionRange(SemanticVersion.parse(minimum), null, false, true);
    }

    /** Returns a range accepting versions from {@code minimum} to {@code maximum}, both inclusive. */
    public static @NotNull VersionRange closed(@NotNull String minimum, @NotNull String maximum) {
        return new VersionRange(SemanticVersion.parse(minimum), SemanticVersion.parse(maximum), true, true);
    }

    /** Returns a range accepting versions below {@code maximum}, exclusive. */
    public static @NotNull VersionRange until(@NotNull String maximum) {
        return new VersionRange(null, SemanticVersion.parse(maximum), true, false);
    }
}
