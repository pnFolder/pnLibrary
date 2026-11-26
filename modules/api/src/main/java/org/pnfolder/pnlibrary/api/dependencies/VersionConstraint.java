package org.pnfolder.pnlibrary.api.dependencies;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;

/** Inclusive lower bound plus one optional upper bound for a dependency version. */
public final class VersionConstraint {
    private final SemanticVersion minimum;
    private final SemanticVersion maximumInclusive;
    private final SemanticVersion maximumExclusive;

    /** Creates a constraint with only a lower bound. */
    public VersionConstraint(@NotNull SemanticVersion minimum) {
        this(minimum, null, null);
    }

    /**
     * Creates a constraint.
     *
     * @param minimum oldest accepted version
     * @param maximumInclusive newest accepted version, inclusive
     * @param maximumExclusive newest boundary, exclusive
     */
    public VersionConstraint(@NotNull SemanticVersion minimum, @Nullable SemanticVersion maximumInclusive,
                             @Nullable SemanticVersion maximumExclusive) {
        if (maximumInclusive != null && maximumExclusive != null) {
            throw new IllegalArgumentException("Only one maximum version bound may be declared");
        }
        if (maximumInclusive != null && maximumInclusive.compareTo(minimum) < 0) {
            throw new IllegalArgumentException("Inclusive maximum version must be >= minimum version");
        }
        if (maximumExclusive != null && maximumExclusive.compareTo(minimum) <= 0) {
            throw new IllegalArgumentException("Exclusive maximum version must be > minimum version");
        }
        this.minimum = minimum;
        this.maximumInclusive = maximumInclusive;
        this.maximumExclusive = maximumExclusive;
    }

    /** Oldest accepted version. */
    @NotNull
    public SemanticVersion getMinimum() {
        return minimum;
    }

    /** Newest accepted version, inclusive, or {@code null}. */
    @Nullable
    public SemanticVersion getMaximumInclusive() {
        return maximumInclusive;
    }

    /** Newest boundary, exclusive, or {@code null}. */
    @Nullable
    public SemanticVersion getMaximumExclusive() {
        return maximumExclusive;
    }

    /** Returns whether {@code version} lies inside this interval. */
    public boolean accepts(@NotNull SemanticVersion version) {
        return version.compareTo(minimum) >= 0
            && (maximumInclusive == null || version.compareTo(maximumInclusive) <= 0)
            && (maximumExclusive == null || version.compareTo(maximumExclusive) < 0);
    }
}
