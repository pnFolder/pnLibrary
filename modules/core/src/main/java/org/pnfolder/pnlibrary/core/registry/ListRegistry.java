package org.pnfolder.pnlibrary.core.registry;

import java.util.List;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.registry.Registry;
import org.pnfolder.pnlibrary.api.registry.RegistryEntry;

/** A {@link Registry} over a list that is asked again on every call, so it always shows what is registered now. */
public final class ListRegistry<T> implements Registry<T> {
    private final String name;
    private final Supplier<List<RegistryEntry<T>>> source;

    public ListRegistry(@NotNull String name, @NotNull Supplier<List<RegistryEntry<T>>> source) {
        this.name = name;
        this.source = source;
    }

    @Override
    public @NotNull String getName() {
        return name;
    }

    @Override
    public @NotNull List<RegistryEntry<T>> entries() {
        return source.get();
    }
}
