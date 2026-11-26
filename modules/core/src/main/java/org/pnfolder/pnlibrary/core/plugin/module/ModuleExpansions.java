package org.pnfolder.pnlibrary.core.plugin.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.expansions.Expansion;
import org.pnfolder.pnlibrary.api.expansions.ExpansionManager;
import org.pnfolder.pnlibrary.api.registry.Registry;
import org.pnfolder.pnlibrary.core.exceptions.CleanupStep;
import org.pnfolder.pnlibrary.core.exceptions.ResourceCleanup;
import org.pnfolder.pnlibrary.core.expansions.ModuleBinding;
import org.pnfolder.pnlibrary.core.expansions.ModuleBoundExpansion;

/**
 * One module's hold on expansions: what it registers ends with it, and it gets its own view of each module-bound
 * library expansion. Lookups read the plugins' registry; the library's own expansions are reached only through {@link #view}.
 */
public final class ModuleExpansions implements ExpansionManager, AutoCloseable {
    private final ExpansionManager shared;
    private final ExpansionManager library;
    private final Object lock = new Object();
    private final Map<Expansion, AutoCloseable> registered = new LinkedHashMap<Expansion, AutoCloseable>();
    private final Map<Class<?>, Object> views = new HashMap<Class<?>, Object>();
    private final List<AutoCloseable> owned = new ArrayList<AutoCloseable>();
    private boolean closed;

    private final ModuleBinding binding = new ModuleBinding() {
        @Override
        public <R extends AutoCloseable> @NotNull R own(@NotNull Supplier<R> acquire) {
            synchronized (lock) {
                checkOpen();
                R resource = acquire.get();
                owned.add(resource);
                return resource;
            }
        }
    };

    public ModuleExpansions(@NotNull ExpansionManager shared, @NotNull ExpansionManager library) {
        this.shared = shared;
        this.library = library;
    }

    @SuppressWarnings("unchecked")
    public <V, E extends ModuleBoundExpansion<V>> @NotNull V view(@NotNull Class<E> type) {
        synchronized (lock) {
            checkOpen();
            Object view = views.get(type);
            if (view == null) {
                E expansion = library.get(type);
                if (expansion == null) {
                    String name = type.getSimpleName();
                    if (name.endsWith("Expansion")) {
                        name = name.substring(0, name.length() - "Expansion".length());
                    }
                    throw new UnsupportedOperationException(name + " is not available on this platform");
                }
                view = expansion.bind(binding);
                views.put(type, view);
            }
            return (V) view;
        }
    }

    @Override
    public @NotNull AutoCloseable register(@NotNull final Expansion expansion) {
        synchronized (lock) {
            checkOpen();
            registered.put(expansion, shared.register(expansion));
            return () -> {
                AutoCloseable removed;
                synchronized (lock) {
                    removed = registered.remove(expansion);
                }
                if (removed != null) {
                    removed.close();
                }
            };
        }
    }

    @Override
    public @Nullable Expansion get(@NotNull String id) {
        return shared.get(id);
    }

    @Override
    public <T extends Expansion> @Nullable T get(@NotNull Class<T> type) {
        return shared.get(type);
    }

    @Override
    public @NotNull List<Expansion> all() {
        return shared.all();
    }

    @Override
    public @NotNull Expansion require(@NotNull String id) {
        return shared.require(id);
    }

    @Override
    public @NotNull Registry<Expansion> registry() {
        return shared.registry();
    }

    @Override
    public void close() {
        List<AutoCloseable> resources;
        synchronized (lock) {
            closed = true;
            views.clear();
            resources = new ArrayList<AutoCloseable>(registered.values());
            resources.addAll(owned);
            registered.clear();
            owned.clear();
        }
        Collections.reverse(resources);
        List<CleanupStep> steps = new ArrayList<CleanupStep>();
        for (final AutoCloseable resource : resources) {
            steps.add(resource::close);
        }
        ResourceCleanup.closeAll(steps);
    }

    private void checkOpen() {
        if (closed) {
            throw new IllegalStateException("Module is closed");
        }
    }
}
