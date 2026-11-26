package org.pnfolder.pnlibrary.updates.engine.catalog;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** Textual compatibility interval for Minecraft or a native platform API, as written in a release catalog. */
public final class VersionRangeText {
    private final String minimum;
    private final String maximum;

    public VersionRangeText(@NotNull String minimum) {
        this(minimum, null);
    }

    public VersionRangeText(@NotNull String minimum, @Nullable String maximum) {
        if (minimum.trim().isEmpty()) {
            throw new IllegalArgumentException("Minimum version must not be blank");
        }
        if (maximum != null && maximum.trim().isEmpty()) {
            throw new IllegalArgumentException("Maximum version must not be blank");
        }
        this.minimum = minimum;
        this.maximum = maximum;
    }

    @NotNull
    public String getMinimum() {
        return minimum;
    }

    @Nullable
    public String getMaximum() {
        return maximum;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof VersionRangeText)) {
            return false;
        }
        VersionRangeText that = (VersionRangeText) other;
        return minimum.equals(that.minimum) && Objects.equals(maximum, that.maximum);
    }

    @Override
    public int hashCode() {
        return Objects.hash(minimum, maximum);
    }

    @Override
    public String toString() {
        return "VersionRangeText(minimum=" + minimum + ", maximum=" + maximum + ")";
    }
}
