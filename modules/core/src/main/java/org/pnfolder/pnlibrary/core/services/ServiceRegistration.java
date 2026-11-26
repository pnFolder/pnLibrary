package org.pnfolder.pnlibrary.core.services;

import java.util.concurrent.atomic.AtomicBoolean;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

/** One registered service; closing it removes it from the registry. */
final class ServiceRegistration<T> implements AutoCloseable {
    final PluginId owner;
    final Class<T> type;
    final T service;
    final int priority;
    final long order;
    private final ServiceRegistry registry;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    ServiceRegistration(ServiceRegistry registry, PluginId owner, Class<T> type, T service, int priority, long order) {
        this.registry = registry;
        this.owner = owner;
        this.type = type;
        this.service = service;
        this.priority = priority;
        this.order = order;
    }

    boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            registry.forget(this);
        }
    }
}
