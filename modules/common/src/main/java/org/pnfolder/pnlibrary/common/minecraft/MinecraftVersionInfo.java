package org.pnfolder.pnlibrary.common.minecraft;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Result of parsing a server version string.
 *
 * <p>The parsed value is still {@link MinecraftVersion#UNKNOWN} when the release is not known by this library, but the
 * original value and extracted numeric coordinates remain available for forward-compatible checks.
 */
public final class MinecraftVersionInfo {
    private final String raw;
    private final MinecraftVersion parsed;
    private final Integer major;
    private final Integer minor;
    private final Integer patch;

    /**
     * @param raw original server-provided version string
     * @param parsed known enum value, or {@link MinecraftVersion#UNKNOWN}
     * @param major extracted major component, when numeric parsing succeeded
     * @param minor extracted minor component, when numeric parsing succeeded
     * @param patch extracted patch component, defaulted to zero when omitted
     */
    public MinecraftVersionInfo(@NotNull String raw, @NotNull MinecraftVersion parsed,
                                @Nullable Integer major, @Nullable Integer minor, @Nullable Integer patch) {
        this.raw = raw;
        this.parsed = parsed;
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    /** The original server-provided version string. */
    @NotNull
    public String getRaw() {
        return raw;
    }

    /** The known enum value, or {@link MinecraftVersion#UNKNOWN}. */
    @NotNull
    public MinecraftVersion getParsed() {
        return parsed;
    }

    /** The extracted major component, when numeric parsing succeeded. */
    @Nullable
    public Integer getMajor() {
        return major;
    }

    /** The extracted minor component, when numeric parsing succeeded. */
    @Nullable
    public Integer getMinor() {
        return minor;
    }

    /** The extracted patch component, or {@code null} when none was found. */
    @Nullable
    public Integer getPatch() {
        return patch;
    }

    /** Whether {@link #getParsed()} is represented by this pnLibrary release. */
    public boolean isKnown() {
        return parsed.isKnown();
    }

    /** {@code true} when a numeric version was found, even if the library does not know it yet. */
    public boolean isNumeric() {
        return major != null && minor != null;
    }

    /**
     * Numeric comparison for unknown future releases. Returns {@code null} when either side is not numeric.
     */
    @Nullable
    public Integer compareNumeric(@NotNull MinecraftVersionInfo other) {
        if (!isNumeric() || !other.isNumeric()) {
            return null;
        }
        int byMajor = Integer.compare(major, other.major);
        if (byMajor != 0) {
            return byMajor;
        }
        int byMinor = Integer.compare(minor, other.minor);
        if (byMinor != 0) {
            return byMinor;
        }
        return Integer.compare(patchOrZero(), other.patchOrZero());
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MinecraftVersionInfo)) {
            return false;
        }
        MinecraftVersionInfo that = (MinecraftVersionInfo) other;
        return raw.equals(that.raw) && parsed == that.parsed
            && Objects.equals(major, that.major) && Objects.equals(minor, that.minor)
            && Objects.equals(patch, that.patch);
    }

    @Override
    public int hashCode() {
        return Objects.hash(raw, parsed, major, minor, patch);
    }

    @Override
    public String toString() {
        return "MinecraftVersionInfo(raw=" + raw + ", parsed=" + parsed + ", major=" + major
            + ", minor=" + minor + ", patch=" + patch + ")";
    }

    private int patchOrZero() {
        return patch == null ? 0 : patch;
    }
}
