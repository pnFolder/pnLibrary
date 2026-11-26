package org.pnfolder.pnlibrary.api.updates.state;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;

/**
 * An installed plugin does not support the library API the plan installs and has no newer release that does.
 * The plugin stays on its version; the library update is not blocked.
 */
public final class UnsupportedApi extends BlockedReason {
    private final ProductId product;
    private final ApiVersionRange supportedApi;
    private final int libraryApi;

    /**
     * Creates the note.
     *
     * @param product plugin that stays on its version
     * @param supportedApi API generations the installed plugin accepts
     * @param libraryApi API generation of the library after the plan
     */
    public UnsupportedApi(@NotNull ProductId product, @NotNull ApiVersionRange supportedApi, int libraryApi) {
        this.product = Objects.requireNonNull(product, "product");
        this.supportedApi = Objects.requireNonNull(supportedApi, "supportedApi");
        this.libraryApi = libraryApi;
    }

    /** Plugin that stays on its version. */
    @NotNull
    public ProductId getProduct() {
        return product;
    }

    /** API generations the installed plugin accepts. */
    @NotNull
    public ApiVersionRange getSupportedApi() {
        return supportedApi;
    }

    /** API generation of the library after the plan. */
    public int getLibraryApi() {
        return libraryApi;
    }

    @Override
    public boolean isAdvisory() {
        return true;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof UnsupportedApi)) {
            return false;
        }
        UnsupportedApi that = (UnsupportedApi) other;
        return libraryApi == that.libraryApi && product.equals(that.product) && supportedApi.equals(that.supportedApi);
    }

    @Override
    public int hashCode() {
        return Objects.hash(product, supportedApi, libraryApi);
    }

    @Override
    public String toString() {
        return "UnsupportedApi(product=" + product + ", supportedApi=" + supportedApi + ", libraryApi=" + libraryApi + ")";
    }
}
