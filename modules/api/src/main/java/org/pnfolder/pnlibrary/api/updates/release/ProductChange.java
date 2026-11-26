package org.pnfolder.pnlibrary.api.updates.release;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;

/** One version transition selected by the resolver. */
public final class ProductChange {
    private final ProductId product;
    private final SemanticVersion from;
    private final SemanticVersion to;

    /**
     * Creates the transition.
     *
     * @param product product being installed or upgraded
     * @param from currently installed version, or {@code null} for a new product
     * @param to selected target version
     */
    public ProductChange(@NotNull ProductId product, @Nullable SemanticVersion from, @NotNull SemanticVersion to) {
        this.product = product;
        this.from = from;
        this.to = to;
    }

    /** Product being installed or upgraded. */
    @NotNull
    public ProductId getProduct() {
        return product;
    }

    /** Currently installed version, or {@code null} for a new product. */
    @Nullable
    public SemanticVersion getFrom() {
        return from;
    }

    /** Selected target version. */
    @NotNull
    public SemanticVersion getTo() {
        return to;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ProductChange)) {
            return false;
        }
        ProductChange that = (ProductChange) other;
        return product.equals(that.product) && Objects.equals(from, that.from) && to.equals(that.to);
    }

    @Override
    public int hashCode() {
        return Objects.hash(product, from, to);
    }

    @Override
    public String toString() {
        return "ProductChange(product=" + product + ", from=" + from + ", to=" + to + ")";
    }
}
