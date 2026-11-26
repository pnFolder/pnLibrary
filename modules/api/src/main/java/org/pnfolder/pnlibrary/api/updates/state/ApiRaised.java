package org.pnfolder.pnlibrary.api.updates.state;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;

/** The plan raises the library API: a risky release, since plugins built for the old API may stop working. */
public final class ApiRaised extends BlockedReason {
    private final ProductId product;
    private final int fromApi;
    private final int toApi;

    /**
     * Creates the note.
     *
     * @param product the library product
     * @param fromApi installed API generation
     * @param toApi API generation after the plan
     */
    public ApiRaised(@NotNull ProductId product, int fromApi, int toApi) {
        this.product = Objects.requireNonNull(product, "product");
        this.fromApi = fromApi;
        this.toApi = toApi;
    }

    /** The library product. */
    @NotNull
    public ProductId getProduct() {
        return product;
    }

    /** Installed API generation. */
    public int getFromApi() {
        return fromApi;
    }

    /** API generation after the plan. */
    public int getToApi() {
        return toApi;
    }

    @Override
    public boolean isAdvisory() {
        return true;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ApiRaised)) {
            return false;
        }
        ApiRaised that = (ApiRaised) other;
        return fromApi == that.fromApi && toApi == that.toApi && product.equals(that.product);
    }

    @Override
    public int hashCode() {
        return Objects.hash(product, fromApi, toApi);
    }

    @Override
    public String toString() {
        return "ApiRaised(product=" + product + ", fromApi=" + fromApi + ", toApi=" + toApi + ")";
    }
}
