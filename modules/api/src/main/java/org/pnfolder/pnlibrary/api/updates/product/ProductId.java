package org.pnfolder.pnlibrary.api.updates.product;

import java.util.Locale;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

/** Stable normalized identity of a pnLibrary-managed product. */
public final class ProductId implements Comparable<ProductId> {
    private static final Pattern VALID = Pattern.compile("[a-z0-9][a-z0-9_.-]*");

    private final String value;

    private ProductId(String value) {
        this.value = value;
    }

    /** Normalizes and validates {@code value}. */
    @NotNull
    public static ProductId of(@NotNull String value) {
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (!VALID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid product ID: " + value);
        }
        return new ProductId(normalized);
    }

    /** Lowercase validated product identifier. */
    @NotNull
    public String getValue() {
        return value;
    }

    /** Compares product identifiers lexicographically. */
    @Override
    public int compareTo(@NotNull ProductId other) {
        return value.compareTo(other.value);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ProductId && value.equals(((ProductId) other).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    /** Returns {@link #getValue()} without additional formatting. */
    @Override
    public String toString() {
        return value;
    }
}
