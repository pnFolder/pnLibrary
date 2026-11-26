package org.pnfolder.pnlibrary.api.updates.backup;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;

/** Version information for a retained component backup. */
public final class UpdateBackupComponent {
    private final ProductId product;
    private final String updatedVersion;
    private final String backupVersion;

    /**
     * Creates the description.
     *
     * @param product managed component identity
     * @param updatedVersion version originally prepared by this transaction, if known
     * @param backupVersion version stored before that update, if known
     */
    public UpdateBackupComponent(@NotNull ProductId product, @Nullable String updatedVersion,
                                 @Nullable String backupVersion) {
        this.product = Objects.requireNonNull(product, "product");
        this.updatedVersion = updatedVersion;
        this.backupVersion = backupVersion;
    }

    /** Managed component identity. */
    @NotNull
    public ProductId getProduct() {
        return product;
    }

    /** Version originally prepared by this transaction, if known. */
    @Nullable
    public String getUpdatedVersion() {
        return updatedVersion;
    }

    /** Version stored before that update, if known. */
    @Nullable
    public String getBackupVersion() {
        return backupVersion;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof UpdateBackupComponent)) {
            return false;
        }
        UpdateBackupComponent that = (UpdateBackupComponent) other;
        return product.equals(that.product) && Objects.equals(updatedVersion, that.updatedVersion)
            && Objects.equals(backupVersion, that.backupVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(product, updatedVersion, backupVersion);
    }

    @Override
    public String toString() {
        return "UpdateBackupComponent(product=" + product + ", updatedVersion=" + updatedVersion
            + ", backupVersion=" + backupVersion + ")";
    }
}
