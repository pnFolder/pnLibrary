package org.pnfolder.pnlibrary.api.remote;

import org.jetbrains.annotations.NotNull;

/** Identity of the product being evaluated. */
public final class ProductInfo {
    private final String id;
    private final String name;
    private final String version;

    /**
     * Creates the identity.
     *
     * @param id stable machine-readable product identifier
     * @param name human-readable product name
     * @param version installed product version
     */
    public ProductInfo(@NotNull String id, @NotNull String name, @NotNull String version) {
        this.id = id;
        this.name = name;
        this.version = version;
    }

    /** Stable machine-readable product identifier. */
    @NotNull
    public String getId() {
        return id;
    }

    /** Human-readable product name. */
    @NotNull
    public String getName() {
        return name;
    }

    /** Installed product version. */
    @NotNull
    public String getVersion() {
        return version;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ProductInfo)) {
            return false;
        }
        ProductInfo that = (ProductInfo) other;
        return id.equals(that.id) && name.equals(that.name) && version.equals(that.version);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * id.hashCode() + name.hashCode()) + version.hashCode();
    }

    @Override
    public String toString() {
        return "ProductInfo(id=" + id + ", name=" + name + ", version=" + version + ")";
    }
}
