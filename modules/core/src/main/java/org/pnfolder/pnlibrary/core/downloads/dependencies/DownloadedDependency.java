package org.pnfolder.pnlibrary.core.downloads.dependencies;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** A downloaded dependency that the server has loaded, with its installed version. */
public final class DownloadedDependency {
    private final String name;
    private final String version;

    /** Creates the pair. */
    public DownloadedDependency(@NotNull String name, @NotNull String version) {
        this.name = Objects.requireNonNull(name, "name");
        this.version = Objects.requireNonNull(version, "version");
    }

    /** The plugin name as the server reports it. */
    public @NotNull String getName() {
        return name;
    }

    /** The installed version. */
    public @NotNull String getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof DownloadedDependency)) {
            return false;
        }
        DownloadedDependency that = (DownloadedDependency) other;
        return name.equals(that.name) && version.equals(that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, version);
    }

    @Override
    public String toString() {
        return "DownloadedDependency(name=" + name + ", version=" + version + ")";
    }
}
