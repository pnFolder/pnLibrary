package org.pnfolder.pnlibrary.api.remote.context;

import java.util.Objects;
import java.util.Locale;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.platform.PlatformType;

/** Runtime platform identity exposed to a remote policy. */
public final class PlatformInfo {
    private final PlatformType type;
    private final String name;
    private final String version;
    private final String key;

    /**
     * Creates the identity.
     *
     * @param type normalized pnLibrary platform family
     * @param name platform implementation name
     * @param version platform implementation version
     */
    public PlatformInfo(@NotNull PlatformType type, @NotNull String name, @NotNull String version) {
        this.type = Objects.requireNonNull(type, "type");
        this.name = Objects.requireNonNull(name, "name");
        this.version = Objects.requireNonNull(version, "version");
        this.key = normalize(name);
    }

    private static String normalize(String value) {
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isLetterOrDigit(c)) {
                result.append(c);
            }
        }
        return result.toString().toLowerCase(Locale.ROOT);
    }

    /** Normalized pnLibrary platform family. */
    @NotNull
    public PlatformType getType() {
        return type;
    }

    /** Platform implementation name. */
    @NotNull
    public String getName() {
        return name;
    }

    /** Platform implementation version. */
    @NotNull
    public String getVersion() {
        return version;
    }

    /** Normalized alphanumeric implementation key used for tolerant comparisons. */
    @NotNull
    public String getKey() {
        return key;
    }

    /** Returns whether the normalized platform name equals {@code value}. */
    public boolean isNamed(@NotNull String value) {
        return key.equals(normalize(value));
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PlatformInfo)) {
            return false;
        }
        PlatformInfo that = (PlatformInfo) other;
        return type == that.type && name.equals(that.name) && version.equals(that.version);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * type.hashCode() + name.hashCode()) + version.hashCode();
    }

    @Override
    public String toString() {
        return "PlatformInfo(type=" + type + ", name=" + name + ", version=" + version + ")";
    }
}
