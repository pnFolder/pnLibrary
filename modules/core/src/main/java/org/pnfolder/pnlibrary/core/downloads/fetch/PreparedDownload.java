package org.pnfolder.pnlibrary.core.downloads.fetch;

import java.nio.file.Path;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** A verified file waiting in {@link #getStaging()} to replace {@link #getTarget()}. */
public final class PreparedDownload {
    private final Path staging;
    private final Path target;

    /** Creates the pair. */
    public PreparedDownload(@NotNull Path staging, @NotNull Path target) {
        this.staging = Objects.requireNonNull(staging, "staging");
        this.target = Objects.requireNonNull(target, "target");
    }

    /** Where the verified file waits. */
    public @NotNull Path getStaging() {
        return staging;
    }

    /** Where the file is published to. */
    public @NotNull Path getTarget() {
        return target;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof PreparedDownload)) {
            return false;
        }
        PreparedDownload that = (PreparedDownload) other;
        return staging.equals(that.staging) && target.equals(that.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(staging, target);
    }

    @Override
    public String toString() {
        return "PreparedDownload(staging=" + staging + ", target=" + target + ")";
    }
}
