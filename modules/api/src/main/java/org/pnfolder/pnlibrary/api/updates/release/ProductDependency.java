package org.pnfolder.pnlibrary.api.updates.release;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;

/** Minimum semantic version required from another managed component. */
public final class ProductDependency {
    private final ProductId product;
    private final SemanticVersion minimumVersion;

    /**
     * Creates the requirement.
     *
     * @param product required managed product
     * @param minimumVersion oldest acceptable product version
     */
    public ProductDependency(@NotNull ProductId product, @NotNull SemanticVersion minimumVersion) {
        this.product = Objects.requireNonNull(product, "product");
        this.minimumVersion = Objects.requireNonNull(minimumVersion, "minimumVersion");
    }

    /** Required managed product. */
    @NotNull
    public ProductId getProduct() {
        return product;
    }

    /** Oldest acceptable product version. */
    @NotNull
    public SemanticVersion getMinimumVersion() {
        return minimumVersion;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ProductDependency)) {
            return false;
        }
        ProductDependency that = (ProductDependency) other;
        return product.equals(that.product) && minimumVersion.equals(that.minimumVersion);
    }

    @Override
    public int hashCode() {
        return 31 * product.hashCode() + minimumVersion.hashCode();
    }

    @Override
    public String toString() {
        return "ProductDependency(product=" + product + ", minimumVersion=" + minimumVersion + ")";
    }
}
