package org.pnfolder.pnlibrary.core.expansions;

import java.util.Objects;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.expansions.Expansion;
import org.pnfolder.pnlibrary.api.expansions.ExpansionManager;
import org.pnfolder.pnlibrary.api.registry.Registry;
import org.pnfolder.pnlibrary.api.registry.RegistryEntry;
import org.pnfolder.pnlibrary.core.exceptions.CleanupStep;
import org.pnfolder.pnlibrary.core.exceptions.ResourceCleanup;
import org.pnfolder.pnlibrary.core.registry.ListRegistry;

/** Thread-safe expansion registry; the failure handler receives every exception an expansion throws while starting or stopping. */
public final class ExpansionRegistry implements ExpansionManager, AutoCloseable {
    private static final String LIBRARY = "pnLibrary";

    private final BiConsumer<Expansion, Exception> onFailure;
    private final Object lock = new Object();
    private final Map<String, Expansion> expansions = new LinkedHashMap<>();
    private boolean closed;

    public ExpansionRegistry(@NotNull BiConsumer<Expansion, Exception> onFailure) {
        this.onFailure = Objects.requireNonNull(onFailure, "onFailure");
    }

    @Override
    public @NotNull AutoCloseable register(@NotNull Expansion expansion) {
        synchronized (lock) {
            if (closed) {
                throw new IllegalStateException("ExpansionManager is closed");
            }
            if (expansions.containsKey(expansion.getId())) {
                throw new IllegalStateException("Expansion '" + expansion.getId() + "' is already registered");
            }
            expansions.put(expansion.getId(), expansion);
        }
        guarded(expansion, true);
        return () -> {
            boolean removed;
            synchronized (lock) {
                removed = expansions.get(expansion.getId()) == expansion && expansions.remove(expansion.getId()) != null;
            }
            if (removed) {
                guarded(expansion, false);
            }
        };
    }

    @Override
    public @Nullable Expansion get(@NotNull String id) {
        synchronized (lock) {
            return expansions.get(id);
        }
    }

    @Override
    public <T extends Expansion> @Nullable T get(@NotNull Class<T> type) {
        for (Expansion expansion : all()) {
            if (type.isInstance(expansion)) {
                return type.cast(expansion);
            }
        }
        return null;
    }

    @Override
    public @NotNull List<Expansion> all() {
        synchronized (lock) {
            return new ArrayList<>(expansions.values());
        }
    }

    @Override
    public @NotNull Registry<Expansion> registry() {
        return new ListRegistry<>("expansions", () -> {
            List<RegistryEntry<Expansion>> entries = new ArrayList<>();
            for (Expansion expansion : all()) {
                entries.add(new RegistryEntry<>(expansion.getId(), expansion, LIBRARY));
            }
            return entries;
        });
    }

    @Override
    public void close() {
        List<Expansion> registered;
        synchronized (lock) {
            closed = true;
            registered = new ArrayList<>(expansions.values());
            expansions.clear();
        }
        List<CleanupStep> steps = new ArrayList<>();
        for (int index = registered.size() - 1; index >= 0; index--) {
            steps.add(registered.get(index)::close);
        }
        ResourceCleanup.closeAll(steps);
    }

    private void guarded(Expansion expansion, boolean enable) {
        try {
            if (enable) {
                expansion.enable();
            } else {
                expansion.disable();
            }
        } catch (Exception error) {
            onFailure.accept(expansion, error);
        }
    }
}
