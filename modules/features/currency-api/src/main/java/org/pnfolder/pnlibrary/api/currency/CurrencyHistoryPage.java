package org.pnfolder.pnlibrary.api.currency;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** One offset-based page of reverse-chronological transaction history. */
public final class CurrencyHistoryPage {
    private final List<CurrencyTransaction> items;
    private final int offset;
    private final boolean hasMore;

    /** Creates a page of {@code items} that starts at {@code offset} in the filtered result. */
    public CurrencyHistoryPage(@NotNull List<CurrencyTransaction> items, int offset, boolean hasMore) {
        this.items = Objects.requireNonNull(items, "items");
        this.offset = offset;
        this.hasMore = hasMore;
    }

    /** Transactions in newest-first order. */
    @NotNull
    public List<CurrencyTransaction> getItems() {
        return items;
    }

    /** Requested zero-based offset in the filtered result. */
    public int getOffset() {
        return offset;
    }

    /** Whether another item exists after this page. */
    public boolean getHasMore() {
        return hasMore;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CurrencyHistoryPage)) {
            return false;
        }
        CurrencyHistoryPage that = (CurrencyHistoryPage) other;
        return offset == that.offset && hasMore == that.hasMore && items.equals(that.items);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * items.hashCode() + offset) + (hasMore ? 1 : 0);
    }

    @Override
    public String toString() {
        return "CurrencyHistoryPage(items=" + items + ", offset=" + offset + ", hasMore=" + hasMore + ")";
    }
}
