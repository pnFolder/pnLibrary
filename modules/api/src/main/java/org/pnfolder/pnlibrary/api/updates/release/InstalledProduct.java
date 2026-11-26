package org.pnfolder.pnlibrary.api.updates.release;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.UpdateValidation;
import org.pnfolder.pnlibrary.api.updates.product.ProductId;
import org.pnfolder.pnlibrary.api.version.ApiVersionRange;
import org.pnfolder.pnlibrary.api.version.SemanticVersion;

/** Installed component state used as the starting point for resolution. */
public final class InstalledProduct {
    private final ProductId product;
    private final SemanticVersion version;
    private final ApiVersionRange supportedApi;
    private final Integer providesApi;

    /**
     * Creates the state.
     *
     * @param product installed product identity
     * @param version currently running version
     * @param supportedApi pnLibrary API generations accepted by this installation
     * @param providesApi API generation supplied by this product, when applicable
     */
    public InstalledProduct(@NotNull ProductId product, @NotNull SemanticVersion version,
                            @NotNull ApiVersionRange supportedApi, @Nullable Integer providesApi) {
        UpdateValidation.requireProvidedApi(providesApi);
        this.product = product;
        this.version = version;
        this.supportedApi = supportedApi;
        this.providesApi = providesApi;
    }

    /** Creates the state of a product that supplies no API generation. */
    public InstalledProduct(@NotNull ProductId product, @NotNull SemanticVersion version,
                            @NotNull ApiVersionRange supportedApi) {
        this(product, version, supportedApi, null);
    }

    /** Installed product identity. */
    @NotNull
    public ProductId getProduct() {
        return product;
    }

    /** Currently running version. */
    @NotNull
    public SemanticVersion getVersion() {
        return version;
    }

    /** pnLibrary API generations accepted by this installation. */
    @NotNull
    public ApiVersionRange getSupportedApi() {
        return supportedApi;
    }

    /** API generation supplied by this product, when applicable. */
    @Nullable
    public Integer getProvidesApi() {
        return providesApi;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof InstalledProduct)) {
            return false;
        }
        InstalledProduct that = (InstalledProduct) other;
        return product.equals(that.product) && version.equals(that.version)
            && supportedApi.equals(that.supportedApi) && Objects.equals(providesApi, that.providesApi);
    }

    @Override
    public int hashCode() {
        return Objects.hash(product, version, supportedApi, providesApi);
    }

    @Override
    public String toString() {
        return "InstalledProduct(product=" + product + ", version=" + version + ", supportedApi=" + supportedApi
            + ", providesApi=" + providesApi + ")";
    }
}
