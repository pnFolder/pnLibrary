package org.pnfolder.pnlibrary.spi.platform;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** JVM metadata collected from the running process. */
public final class JavaSnapshot {
    private final String version;
    private final String vendor;
    private final String name;

    /**
     * @param version Java runtime version
     * @param vendor Java runtime vendor
     * @param name JVM implementation name
     */
    public JavaSnapshot(@NotNull String version, @NotNull String vendor, @NotNull String name) {
        this.version = version;
        this.vendor = vendor;
        this.name = name;
    }

    /** Reads the metadata of the running JVM; unavailable values are {@code unknown}. */
    @NotNull
    public static JavaSnapshot current() {
        return new JavaSnapshot(
                System.getProperty("java.version", "unknown"),
                System.getProperty("java.vendor", "unknown"),
                System.getProperty("java.vm.name", "unknown"));
    }

    /** Java runtime version. */
    @NotNull
    public String getVersion() {
        return version;
    }

    /** Java runtime vendor. */
    @NotNull
    public String getVendor() {
        return vendor;
    }

    /** JVM implementation name. */
    @NotNull
    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof JavaSnapshot)) {
            return false;
        }
        JavaSnapshot that = (JavaSnapshot) other;
        return version.equals(that.version) && vendor.equals(that.vendor) && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, vendor, name);
    }

    @Override
    public String toString() {
        return "JavaSnapshot(version=" + version + ", vendor=" + vendor + ", name=" + name + ")";
    }
}
