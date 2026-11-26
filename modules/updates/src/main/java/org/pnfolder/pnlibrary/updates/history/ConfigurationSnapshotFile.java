package org.pnfolder.pnlibrary.updates.history;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.backup.BackupKind;

/** One file of a snapshot: where it lives, and the saved copy ({@code blob}) with its size and checksum. */
public final class ConfigurationSnapshotFile {
    private final String product;
    private final String root;
    private final String relative;
    private final boolean existed;
    private final String blob;
    private final long size;
    private final String sha256;
    private final String kind;
    private final String name;

    /** Creates a record of a configuration file. */
    public ConfigurationSnapshotFile(@NotNull String product, @NotNull String root, @NotNull String relative,
                                     boolean existed, @Nullable String blob, long size, @Nullable String sha256) {
        this(product, root, relative, existed, blob, size, sha256, null, null);
    }

    /** Creates a record; {@code kind} is the name of a {@link BackupKind}, null for configuration. */
    public ConfigurationSnapshotFile(@NotNull String product, @NotNull String root, @NotNull String relative,
                                     boolean existed, @Nullable String blob, long size, @Nullable String sha256,
                                     @Nullable String kind, @Nullable String name) {
        this.product = Objects.requireNonNull(product, "product");
        this.root = Objects.requireNonNull(root, "root");
        this.relative = Objects.requireNonNull(relative, "relative");
        this.existed = existed;
        this.blob = blob;
        this.size = size;
        this.sha256 = sha256;
        this.kind = kind;
        this.name = name;
    }

    /** The product the file belongs to. */
    @NotNull
    public String getProduct() {
        return product;
    }

    /** The data folder of the product. */
    @NotNull
    public String getRoot() {
        return root;
    }

    /** The path of the file relative to {@link #getRoot()}. */
    @NotNull
    public String getRelative() {
        return relative;
    }

    /** Whether the file existed when the snapshot was taken. */
    public boolean getExisted() {
        return existed;
    }

    /** The name of the saved copy, or null when the file did not exist. */
    @Nullable
    public String getBlob() {
        return blob;
    }

    /** The size of the saved copy in bytes. */
    public long getSize() {
        return size;
    }

    /** The SHA-256 of the saved copy, or null when the file did not exist. */
    @Nullable
    public String getSha256() {
        return sha256;
    }

    /** The name of the {@link BackupKind}, or null in snapshots from older versions. */
    @Nullable
    public String getKind() {
        return kind;
    }

    /** The display name of the file, or null. */
    @Nullable
    public String getName() {
        return name;
    }

    /** What the file holds; snapshots from older versions held configuration only. */
    @NotNull
    public BackupKind getBackupKind() {
        return kind == null ? BackupKind.CONFIGURATION : BackupKind.valueOf(kind);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ConfigurationSnapshotFile)) {
            return false;
        }
        ConfigurationSnapshotFile that = (ConfigurationSnapshotFile) other;
        return existed == that.existed && size == that.size && product.equals(that.product)
            && root.equals(that.root) && relative.equals(that.relative) && Objects.equals(blob, that.blob)
            && Objects.equals(sha256, that.sha256) && Objects.equals(kind, that.kind)
            && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(product, root, relative, existed, blob, size, sha256, kind, name);
    }

    @Override
    public String toString() {
        return "ConfigurationSnapshotFile(product=" + product + ", root=" + root + ", relative=" + relative
            + ", existed=" + existed + ", blob=" + blob + ", size=" + size + ", sha256=" + sha256
            + ", kind=" + kind + ", name=" + name + ")";
    }
}
