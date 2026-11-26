package org.pnfolder.pnlibrary.updates.engine.catalog;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/** Validated cached catalog entry. */
public final class CatalogCacheHit {
    private final byte[] bytes;
    private final boolean fresh;

    /**
     * @param bytes encoded catalog bytes
     * @param fresh whether the cache age is within the requested TTL
     */
    public CatalogCacheHit(@NotNull byte[] bytes, boolean fresh) {
        this.bytes = Objects.requireNonNull(bytes, "bytes");
        this.fresh = fresh;
    }

    @NotNull
    public byte[] getBytes() {
        return bytes;
    }

    public boolean getFresh() {
        return fresh;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CatalogCacheHit)) {
            return false;
        }
        CatalogCacheHit that = (CatalogCacheHit) other;
        return fresh == that.fresh && Arrays.equals(bytes, that.bytes);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(bytes) + (fresh ? 1 : 0);
    }

    @Override
    public String toString() {
        return "CatalogCacheHit(bytes=" + bytes.length + " bytes, fresh=" + fresh + ")";
    }
}
