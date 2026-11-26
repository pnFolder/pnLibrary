package org.pnfolder.pnlibrary.core.observability.store;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Objects;

/** A file attached to an observation and copied into library storage. */
public final class StoredAttachment {
    private final String id;
    private final String observationId;
    private final String originalName;
    private final String contentType;
    private final long size;
    private final String sha256;
    private final Path storedPath;

    public StoredAttachment(@NotNull String id, @NotNull String observationId, @NotNull String originalName,
                            @NotNull String contentType, long size, @NotNull String sha256, @NotNull Path storedPath) {
        this.id = Objects.requireNonNull(id, "id");
        this.observationId = Objects.requireNonNull(observationId, "observationId");
        this.originalName = Objects.requireNonNull(originalName, "originalName");
        this.contentType = Objects.requireNonNull(contentType, "contentType");
        this.size = size;
        this.sha256 = Objects.requireNonNull(sha256, "sha256");
        this.storedPath = Objects.requireNonNull(storedPath, "storedPath");
    }

    @NotNull
    public String getId() {
        return id;
    }

    @NotNull
    public String getObservationId() {
        return observationId;
    }

    @NotNull
    public String getOriginalName() {
        return originalName;
    }

    @NotNull
    public String getContentType() {
        return contentType;
    }

    public long getSize() {
        return size;
    }

    @NotNull
    public String getSha256() {
        return sha256;
    }

    @NotNull
    public Path getStoredPath() {
        return storedPath;
    }

    /** The same attachment stored at another path. */
    @NotNull
    public StoredAttachment withStoredPath(@NotNull Path path) {
        return new StoredAttachment(id, observationId, originalName, contentType, size, sha256, path);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StoredAttachment)) {
            return false;
        }
        StoredAttachment that = (StoredAttachment) other;
        return size == that.size && id.equals(that.id) && observationId.equals(that.observationId)
                && originalName.equals(that.originalName) && contentType.equals(that.contentType)
                && sha256.equals(that.sha256) && storedPath.equals(that.storedPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, observationId, originalName, contentType, size, sha256, storedPath);
    }

    @NotNull
    @Override
    public String toString() {
        return "StoredAttachment(id=" + id + ", observationId=" + observationId + ", originalName=" + originalName
                + ", contentType=" + contentType + ", size=" + size + ", sha256=" + sha256 + ", storedPath=" + storedPath + ")";
    }
}
