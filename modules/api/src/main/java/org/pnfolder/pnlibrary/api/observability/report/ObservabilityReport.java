package org.pnfolder.pnlibrary.api.observability.report;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Objects;

/** Identifies a support report stored on the local filesystem. */
public final class ObservabilityReport {
    private final String id;
    private final Path file;
    private final long createdAt;

    /**
     * Creates the identity of a report.
     *
     * @param id unique report identifier
     * @param file path to the generated report archive
     * @param createdAt creation time expressed as Unix epoch milliseconds
     */
    public ObservabilityReport(@NotNull String id, @NotNull Path file, long createdAt) {
        this.id = Objects.requireNonNull(id);
        this.file = Objects.requireNonNull(file);
        this.createdAt = createdAt;
    }

    /** The unique report identifier. */
    @NotNull
    public String getId() {
        return id;
    }

    /** The path to the generated report archive. */
    @NotNull
    public Path getFile() {
        return file;
    }

    /** The creation time expressed as Unix epoch milliseconds. */
    public long getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ObservabilityReport)) {
            return false;
        }
        ObservabilityReport that = (ObservabilityReport) other;
        return createdAt == that.createdAt && id.equals(that.id) && file.equals(that.file);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, file, createdAt);
    }

    @NotNull
    @Override
    public String toString() {
        return "ObservabilityReport(id=" + id + ", file=" + file + ", createdAt=" + createdAt + ")";
    }
}
