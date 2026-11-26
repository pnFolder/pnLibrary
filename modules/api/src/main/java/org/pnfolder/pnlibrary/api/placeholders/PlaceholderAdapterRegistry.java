package org.pnfolder.pnlibrary.api.placeholders;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** The adapters of the outside placeholder systems that are connected. */
public interface PlaceholderAdapterRegistry {
    /** Registers {@code adapter} and returns a handle that removes and closes it. */
    @NotNull
    AutoCloseable register(@NotNull PlaceholderAdapter adapter);

    /** The adapter with case-insensitive {@code id}, or {@code null}. */
    @Nullable
    PlaceholderAdapter get(@NotNull String id);

    /** The adapter or a failure when it is not connected. */
    @NotNull
    default PlaceholderAdapter require(@NotNull String id) {
        PlaceholderAdapter adapter = get(id);
        if (adapter == null) {
            throw new IllegalStateException("Placeholder adapter " + id + " is unavailable");
        }
        return adapter;
    }

    /** A stable snapshot of the adapters ordered by identifier. */
    @NotNull
    List<PlaceholderAdapter> all();
}
