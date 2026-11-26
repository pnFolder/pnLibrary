package org.pnfolder.pnlibrary.api.updates;

import java.util.List;
import java.util.concurrent.CompletionStage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.updates.history.UpdateHistoryEntry;

/** The durable log of updates: checks, downloads, installs, rollbacks and changed settings. */
public interface UpdateHistory {
    /** Ten events newest first for all products; {@code page} counts from one. */
    @NotNull
    default CompletionStage<List<UpdateHistoryEntry>> page(int page) {
        return page(page, null);
    }

    /** Ten events newest first; {@code page} counts from one, {@code product} limits them to one managed product id. */
    @NotNull
    CompletionStage<List<UpdateHistoryEntry>> page(int page, @Nullable String product);

    /** How many events {@link #page(int)} pages through, for all products. */
    @NotNull
    default CompletionStage<Integer> count() {
        return count(null);
    }

    /** How many events {@link #page(int, String)} pages through, for all products or for one {@code product}. */
    @NotNull
    CompletionStage<Integer> count(@Nullable String product);
}
