package org.pnfolder.pnlibrary.api.version;

import java.util.Objects;

/** Inclusive pnLibrary API generations supported by a component release. */
public final class ApiVersionRange {
    private final int minimum;
    private final int maximum;

    /**
     * @param minimum oldest supported API generation
     * @param maximum newest supported API generation
     */
    public ApiVersionRange(int minimum, int maximum) {
        if (minimum <= 0) {
            throw new IllegalArgumentException("Minimum API generation must be positive");
        }
        if (maximum < minimum) {
            throw new IllegalArgumentException("Maximum API generation must be at least minimum");
        }
        this.minimum = minimum;
        this.maximum = maximum;
    }

    /** The oldest supported API generation. */
    public int getMinimum() {
        return minimum;
    }

    /** The newest supported API generation. */
    public int getMaximum() {
        return maximum;
    }

    /** Returns whether {@code version} is inside this inclusive range. */
    public boolean supports(int version) {
        return version >= minimum && version <= maximum;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ApiVersionRange)) {
            return false;
        }
        ApiVersionRange that = (ApiVersionRange) other;
        return minimum == that.minimum && maximum == that.maximum;
    }

    @Override
    public int hashCode() {
        return Objects.hash(minimum, maximum);
    }

    @Override
    public String toString() {
        return "ApiVersionRange(minimum=" + minimum + ", maximum=" + maximum + ")";
    }
}
