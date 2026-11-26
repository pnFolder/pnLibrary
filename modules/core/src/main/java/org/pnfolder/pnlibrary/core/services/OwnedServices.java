package org.pnfolder.pnlibrary.core.services;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.api.registry.Registry;

/** The services one plugin module registered; they end with the module. */
final class OwnedServices implements ServiceScope {
    private final ServiceRegistry registry;
    private final PluginId owner;
    private final AtomicBoolean ownerClosed = new AtomicBoolean(false);

    OwnedServices(ServiceRegistry registry, PluginId owner) {
        this.registry = registry;
        this.owner = owner;
    }

    @Override
    public <T> void register(@NotNull Class<T> type, @NotNull T service, int priority) {
        synchronized (registry.lock()) {
            if (ownerClosed.get()) {
                throw new IllegalStateException("Services for plugin " + owner + " are closed");
            }
            registry.register(owner, type, service, priority);
        }
    }

    @Override
    public <T> void unregister(@NotNull Class<T> type) {
        registry.unregister(owner, type);
    }

    @Override
    public <T> @Nullable T get(@NotNull Class<T> type) {
        return registry.get(type);
    }

    @Override
    public <T> @NotNull List<T> all(@NotNull Class<T> type) {
        return registry.all(type);
    }

    @Override
    public @NotNull Registry<Object> registry() {
        return registry.registry();
    }

    @Override
    public void close() {
        if (ownerClosed.compareAndSet(false, true)) {
            registry.unregisterAll(owner);
        }
    }
}
