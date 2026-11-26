package org.pnfolder.pnlibrary.api.updates.backup;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;

/** One saved file of a backup: what it holds, what it is called and how large it is. */
public final class UpdateBackupItem {
    private final ProductId product;
    private final BackupKind kind;
    private final String name;
    private final long bytes;

    /** Creates the description of one saved file. */
    public UpdateBackupItem(@NotNull ProductId product, @NotNull BackupKind kind, @NotNull String name, long bytes) {
        this.product = product;
        this.kind = kind;
        this.name = name;
        this.bytes = bytes;
    }

    /** Product the file belongs to. */
    @NotNull
    public ProductId getProduct() {
        return product;
    }

    /** What the file holds. */
    @NotNull
    public BackupKind getKind() {
        return kind;
    }

    /** Display name of the file. */
    @NotNull
    public String getName() {
        return name;
    }

    /** Size of the file in bytes. */
    public long getBytes() {
        return bytes;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof UpdateBackupItem)) {
            return false;
        }
        UpdateBackupItem that = (UpdateBackupItem) other;
        return bytes == that.bytes && product.equals(that.product) && kind == that.kind && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(product, kind, name, bytes);
    }

    @Override
    public String toString() {
        return "UpdateBackupItem(product=" + product + ", kind=" + kind + ", name=" + name + ", bytes=" + bytes + ")";
    }
}
