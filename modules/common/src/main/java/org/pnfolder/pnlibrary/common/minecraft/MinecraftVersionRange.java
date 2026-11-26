package org.pnfolder.pnlibrary.common.minecraft;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Minecraft version range with configurable boundaries.
 *
 * <p>A missing minimum means there is no lower bound; a missing maximum means there is no upper bound.
 * {@link MinecraftVersion#UNKNOWN} cannot be a boundary and never belongs to a range. Prefer the factory methods:
 *
 * <pre>{@code
 * MinecraftVersionRange legacy = MinecraftVersionRange.between(MinecraftVersion.V1_8_8, MinecraftVersion.V1_12_2);
 * if (legacy.contains(MinecraftVersion.parse("1.10.2"))) {
 *     enableLegacyApi();
 * }
 * }</pre>
 */
public final class MinecraftVersionRange {
    private final MinecraftVersion minimum;
    private final MinecraftVersion maximum;
    private final boolean includeMinimum;
    private final boolean includeMaximum;

    /**
     * @param minimum lower boundary, or {@code null}
     * @param maximum upper boundary, or {@code null}
     * @param includeMinimum whether the lower boundary is included
     * @param includeMaximum whether the upper boundary is included
     * @throws IllegalArgumentException when a boundary is UNKNOWN or the minimum is newer than the maximum
     */
    public MinecraftVersionRange(@Nullable MinecraftVersion minimum, @Nullable MinecraftVersion maximum,
                                 boolean includeMinimum, boolean includeMaximum) {
        if ((minimum != null && !minimum.isKnown()) || (maximum != null && !maximum.isKnown())) {
            throw new IllegalArgumentException("UNKNOWN cannot be a range boundary");
        }
        if (minimum != null && maximum != null && minimum.isNewerThan(maximum)) {
            throw new IllegalArgumentException("Minimum " + minimum.getText() + " is newer than maximum "
                + maximum.getText());
        }
        this.minimum = minimum;
        this.maximum = maximum;
        this.includeMinimum = includeMinimum;
        this.includeMaximum = includeMaximum;
    }

    /** Inclusive range {@code [minimum, maximum]}. */
    @NotNull
    public static MinecraftVersionRange between(@NotNull MinecraftVersion minimum, @NotNull MinecraftVersion maximum) {
        return new MinecraftVersionRange(minimum, maximum, true, true);
    }

    /** Exclusive range {@code (minimum, maximum)}. */
    @NotNull
    public static MinecraftVersionRange betweenExclusive(@NotNull MinecraftVersion minimum,
                                                         @NotNull MinecraftVersion maximum) {
        return new MinecraftVersionRange(minimum, maximum, false, false);
    }

    /** Range {@code [minimum, +∞)} without an upper boundary. */
    @NotNull
    public static MinecraftVersionRange from(@NotNull MinecraftVersion minimum) {
        return from(minimum, true);
    }

    /** Range from {@code minimum} without an upper boundary; {@code inclusive} decides whether minimum is included. */
    @NotNull
    public static MinecraftVersionRange from(@NotNull MinecraftVersion minimum, boolean inclusive) {
        return new MinecraftVersionRange(minimum, null, inclusive, false);
    }

    /** Range {@code (-∞, maximum]} without a lower boundary. */
    @NotNull
    public static MinecraftVersionRange until(@NotNull MinecraftVersion maximum) {
        return until(maximum, true);
    }

    /** Range up to {@code maximum} without a lower boundary; {@code inclusive} decides whether maximum is included. */
    @NotNull
    public static MinecraftVersionRange until(@NotNull MinecraftVersion maximum, boolean inclusive) {
        return new MinecraftVersionRange(null, maximum, false, inclusive);
    }

    /** Range {@code [minimum, +∞)}. */
    @NotNull
    public static MinecraftVersionRange atLeast(@NotNull MinecraftVersion minimum) {
        return from(minimum, true);
    }

    /** Range {@code (minimum, +∞)}. */
    @NotNull
    public static MinecraftVersionRange newerThan(@NotNull MinecraftVersion minimum) {
        return from(minimum, false);
    }

    /** Range {@code (-∞, maximum]}. */
    @NotNull
    public static MinecraftVersionRange atMost(@NotNull MinecraftVersion maximum) {
        return until(maximum, true);
    }

    /** Range {@code (-∞, maximum)}. */
    @NotNull
    public static MinecraftVersionRange olderThan(@NotNull MinecraftVersion maximum) {
        return until(maximum, false);
    }

    /** Range containing only {@code version}. */
    @NotNull
    public static MinecraftVersionRange exact(@NotNull MinecraftVersion version) {
        return new MinecraftVersionRange(version, version, true, true);
    }

    /** Every known version; {@link MinecraftVersion#UNKNOWN} is excluded. */
    @NotNull
    public static MinecraftVersionRange allKnown() {
        return new MinecraftVersionRange(null, null, false, false);
    }

    /** The lower boundary, or {@code null} when there is none. */
    @Nullable
    public MinecraftVersion getMinimum() {
        return minimum;
    }

    /** The upper boundary, or {@code null} when there is none. */
    @Nullable
    public MinecraftVersion getMaximum() {
        return maximum;
    }

    /** Whether the lower boundary is included. */
    public boolean isIncludeMinimum() {
        return includeMinimum;
    }

    /** Whether the upper boundary is included. */
    public boolean isIncludeMaximum() {
        return includeMaximum;
    }

    /** Whether {@code version} belongs to this range. {@link MinecraftVersion#UNKNOWN} never does. */
    public boolean contains(@NotNull MinecraftVersion version) {
        if (!version.isKnown()) {
            return false;
        }
        boolean afterMinimum = minimum == null
            || (includeMinimum ? version.isAtLeast(minimum) : version.isNewerThan(minimum));
        boolean beforeMaximum = maximum == null
            || (includeMaximum ? version.isAtMost(maximum) : version.isOlderThan(maximum));
        return afterMinimum && beforeMaximum;
    }

    /** Returns {@code true} when {@code version} does not belong to this range. */
    public boolean excludes(@NotNull MinecraftVersion version) {
        return !contains(version);
    }

    /** Returns {@code true} when this range contains exactly one version. */
    public boolean isExact() {
        return minimum != null && minimum == maximum && includeMinimum && includeMaximum;
    }

    /** Returns whether this range and {@code other} share at least one version. */
    public boolean overlaps(@NotNull MinecraftVersionRange other) {
        return intersection(other) != null;
    }

    /**
     * Returns the intersection with {@code other}, or {@code null} when none exists. Inclusive and exclusive boundaries
     * are preserved.
     */
    @Nullable
    public MinecraftVersionRange intersection(@NotNull MinecraftVersionRange other) {
        MinecraftVersion min = newer(minimum, other.minimum);
        MinecraftVersion max = older(maximum, other.maximum);
        if (min != null && max != null && min.isNewerThan(max)) {
            return null;
        }
        boolean includeMin = minimumIncluded(min, this) && minimumIncluded(min, other);
        boolean includeMax = maximumIncluded(max, this) && maximumIncluded(max, other);
        if (min != null && min == max && !(includeMin && includeMax)) {
            return null;
        }
        return new MinecraftVersionRange(min, max, includeMin, includeMax);
    }

    /** Formats a value such as {@code [1.8.8, 1.12.2]}, {@code >=1.20.5}, or {@code <1.13}. */
    @Override
    public String toString() {
        if (minimum != null && maximum != null) {
            return (includeMinimum ? "[" : "(") + minimum.getText() + ", " + maximum.getText()
                + (includeMaximum ? "]" : ")");
        }
        if (minimum != null) {
            return (includeMinimum ? ">=" : ">") + minimum.getText();
        }
        if (maximum != null) {
            return (includeMaximum ? "<=" : "<") + maximum.getText();
        }
        return "all known versions";
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MinecraftVersionRange)) {
            return false;
        }
        MinecraftVersionRange that = (MinecraftVersionRange) other;
        return minimum == that.minimum && maximum == that.maximum
            && includeMinimum == that.includeMinimum && includeMaximum == that.includeMaximum;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minimum, maximum, includeMinimum, includeMaximum);
    }

    private static MinecraftVersion newer(MinecraftVersion a, MinecraftVersion b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isAtLeast(b) ? a : b;
    }

    private static MinecraftVersion older(MinecraftVersion a, MinecraftVersion b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.isAtMost(b) ? a : b;
    }

    private static boolean minimumIncluded(@Nullable MinecraftVersion value, MinecraftVersionRange range) {
        if (value == null) {
            return true;
        }
        return range.minimum == null || !value.equals(range.minimum) || range.includeMinimum;
    }

    private static boolean maximumIncluded(@Nullable MinecraftVersion value, MinecraftVersionRange range) {
        if (value == null) {
            return true;
        }
        return range.maximum == null || !value.equals(range.maximum) || range.includeMaximum;
    }
}
