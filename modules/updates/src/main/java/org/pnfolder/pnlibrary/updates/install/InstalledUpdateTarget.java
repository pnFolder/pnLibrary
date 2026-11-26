package org.pnfolder.pnlibrary.updates.install;

import java.nio.file.Path;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;

/** Where an installed component lives and where its update is placed for the next restart. */
public final class InstalledUpdateTarget {
    private final Path currentJar;
    private final Path updateDirectory;
    private final ApiVersionRange supportedApi;

    /**
     * Creates a target.
     *
     * @param currentJar the jar now in use
     * @param updateDirectory folder the new jar is placed in
     * @param supportedApi API versions the component accepts, null when it does not say
     */
    public InstalledUpdateTarget(@NotNull Path currentJar, @NotNull Path updateDirectory,
                                 @Nullable ApiVersionRange supportedApi) {
        this.currentJar = Objects.requireNonNull(currentJar, "currentJar");
        this.updateDirectory = Objects.requireNonNull(updateDirectory, "updateDirectory");
        this.supportedApi = supportedApi;
    }

    /** Creates a target of a component that does not say which API versions it accepts. */
    public InstalledUpdateTarget(@NotNull Path currentJar, @NotNull Path updateDirectory) {
        this(currentJar, updateDirectory, null);
    }

    /** The jar now in use. */
    @NotNull
    public Path getCurrentJar() {
        return currentJar;
    }

    /** Folder the new jar is placed in. */
    @NotNull
    public Path getUpdateDirectory() {
        return updateDirectory;
    }

    /** API versions the component accepts, null when it does not say. */
    @Nullable
    public ApiVersionRange getSupportedApi() {
        return supportedApi;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof InstalledUpdateTarget)) {
            return false;
        }
        InstalledUpdateTarget that = (InstalledUpdateTarget) other;
        return currentJar.equals(that.currentJar) && updateDirectory.equals(that.updateDirectory)
                && Objects.equals(supportedApi, that.supportedApi);
    }

    @Override
    public int hashCode() {
        return Objects.hash(currentJar, updateDirectory, supportedApi);
    }

    @Override
    public String toString() {
        return "InstalledUpdateTarget(currentJar=" + currentJar + ", updateDirectory=" + updateDirectory
                + ", supportedApi=" + supportedApi + ")";
    }
}
