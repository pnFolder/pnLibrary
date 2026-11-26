package org.pnfolder.pnlibrary.api.updates.state;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;

/**
 * A plugin has a release for a newer API than the library has. It is not downloaded until the library
 * reaches that API; the next check then installs it.
 */
public final class WaitsForLibrary extends BlockedReason {
    private final ProductId product;
    private final SemanticVersion version;
    private final int requiredApi;
    private final int libraryApi;

    /**
     * Creates the note.
     *
     * @param product plugin that waits
     * @param version newest plugin release that needs the newer API
     * @param requiredApi API generation that release needs
     * @param libraryApi API generation of the library after the plan
     */
    public WaitsForLibrary(@NotNull ProductId product, @NotNull SemanticVersion version, int requiredApi, int libraryApi) {
        this.product = Objects.requireNonNull(product, "product");
        this.version = Objects.requireNonNull(version, "version");
        this.requiredApi = requiredApi;
        this.libraryApi = libraryApi;
    }

    /** Plugin that waits. */
    @NotNull
    public ProductId getProduct() {
        return product;
    }

    /** Newest plugin release that needs the newer API. */
    @NotNull
    public SemanticVersion getVersion() {
        return version;
    }

    /** API generation that release needs. */
    public int getRequiredApi() {
        return requiredApi;
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
        if (!(other instanceof WaitsForLibrary)) {
            return false;
        }
        WaitsForLibrary that = (WaitsForLibrary) other;
        return requiredApi == that.requiredApi && libraryApi == that.libraryApi && product.equals(that.product)
            && version.equals(that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(product, version, requiredApi, libraryApi);
    }

    @Override
    public String toString() {
        return "WaitsForLibrary(product=" + product + ", version=" + version + ", requiredApi=" + requiredApi
            + ", libraryApi=" + libraryApi + ")";
    }
}
