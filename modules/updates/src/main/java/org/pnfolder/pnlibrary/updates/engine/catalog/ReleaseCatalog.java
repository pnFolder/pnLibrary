package org.pnfolder.pnlibrary.updates.engine.catalog;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Parsed release catalog. */
public final class ReleaseCatalog {
    private final String product;
    private final List<CatalogRelease> releases;

    /**
     * @param product normalized product identifier
     * @param releases ordered published releases
     */
    public ReleaseCatalog(@NotNull String product, @NotNull List<CatalogRelease> releases) {
        this.product = Objects.requireNonNull(product, "product");
        this.releases = Collections.unmodifiableList(new ArrayList<>(releases));
    }

    @NotNull
    public String getProduct() {
        return product;
    }

    @NotNull
    public List<CatalogRelease> getReleases() {
        return releases;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ReleaseCatalog)) {
            return false;
        }
        ReleaseCatalog that = (ReleaseCatalog) other;
        return product.equals(that.product) && releases.equals(that.releases);
    }

    @Override
    public int hashCode() {
        return 31 * product.hashCode() + releases.hashCode();
    }

    @Override
    public String toString() {
        return "ReleaseCatalog(product=" + product + ", releases=" + releases + ")";
    }
}
