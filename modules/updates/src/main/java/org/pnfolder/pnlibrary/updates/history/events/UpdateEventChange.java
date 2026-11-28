package org.pnfolder.pnlibrary.updates.history.events;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A product that moved from {@code from} (null when it was absent) to {@code to}. */
public final class UpdateEventChange {
    private final String product;
    private final String from;
    private final String to;

    /** Creates a change. */
    public UpdateEventChange(@NotNull String product, @Nullable String from, @NotNull String to) {
        this.product = Objects.requireNonNull(product, "product");
        this.from = from;
        this.to = Objects.requireNonNull(to, "to");
    }

    /** The product that changed. */
    @NotNull
    public String getProduct() {
        return product;
    }

    /** The version before, or null when the product was absent. */
    @Nullable
    public String getFrom() {
        return from;
    }

    /** The version after. */
    @NotNull
    public String getTo() {
        return to;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof UpdateEventChange)) {
            return false;
        }
        UpdateEventChange that = (UpdateEventChange) other;
        return product.equals(that.product) && Objects.equals(from, that.from) && to.equals(that.to);
    }

    @Override
    public int hashCode() {
        return Objects.hash(product, from, to);
    }

    @Override
    public String toString() {
        return "Change(product=" + product + ", from=" + from + ", to=" + to + ")";
    }
}
