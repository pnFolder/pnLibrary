package org.pnfolder.pnlibrary.updates.history;

import java.util.List;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** The manifest of a snapshot: the plan it was taken for and its files. */
public final class ConfigurationSnapshot {
    private final int schema;
    private final String id;
    private final String planId;
    private final String createdAt;
    private final List<ConfigurationSnapshotFile> files;

    /** Creates a manifest. */
    public ConfigurationSnapshot(int schema, @NotNull String id, @NotNull String planId, @NotNull String createdAt,
                                 @NotNull List<ConfigurationSnapshotFile> files) {
        this.schema = schema;
        this.id = Objects.requireNonNull(id, "id");
        this.planId = Objects.requireNonNull(planId, "planId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.files = Objects.requireNonNull(files, "files");
    }

    /** The version of the manifest format. */
    public int getSchema() {
        return schema;
    }

    /** The snapshot id. */
    @NotNull
    public String getId() {
        return id;
    }

    /** The id of the update plan the snapshot was taken for. */
    @NotNull
    public String getPlanId() {
        return planId;
    }

    /** When the snapshot was taken, as ISO-8601 text. */
    @NotNull
    public String getCreatedAt() {
        return createdAt;
    }

    /** The files of the snapshot. */
    @NotNull
    public List<ConfigurationSnapshotFile> getFiles() {
        return files;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ConfigurationSnapshot)) {
            return false;
        }
        ConfigurationSnapshot that = (ConfigurationSnapshot) other;
        return schema == that.schema && id.equals(that.id) && planId.equals(that.planId)
            && createdAt.equals(that.createdAt) && files.equals(that.files);
    }

    @Override
    public int hashCode() {
        return Objects.hash(schema, id, planId, createdAt, files);
    }

    @Override
    public String toString() {
        return "ConfigurationSnapshot(schema=" + schema + ", id=" + id + ", planId=" + planId
            + ", createdAt=" + createdAt + ", files=" + files + ")";
    }
}
