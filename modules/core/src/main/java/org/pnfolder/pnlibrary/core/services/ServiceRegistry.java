package org.pnfolder.pnlibrary.core.services;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.api.registry.Registry;
import org.pnfolder.pnlibrary.api.registry.RegistryEntry;
import org.pnfolder.pnlibrary.api.services.ServiceManager;
import org.pnfolder.pnlibrary.core.registry.ListRegistry;

/** Thread-safe implementation of the typed, plugin-owned service registry. */
public class ServiceRegistry implements ServiceManager, AutoCloseable {
    private final Object lock = new Object();
    private final Map<Class<?>, List<ServiceRegistration<?>>> providers = new HashMap<>();
    private final Map<PluginId, List<ServiceRegistration<?>>> registrationsByOwner = new HashMap<>();
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    @Override
    public <T> void register(@NotNull Class<T> type, @NotNull T service, int priority) {
        add(null, type, service, priority);
    }

    /** Registers a service for a plugin with the default priority. */
    public <T> void register(@NotNull PluginId owner, @NotNull Class<T> type, @NotNull T service) {
        add(owner, type, service, 0);
    }

    /** Registers a service for a plugin. */
    public <T> void register(@NotNull PluginId owner, @NotNull Class<T> type, @NotNull T service, int priority) {
        add(owner, type, service, priority);
    }

    @Override
    public <T> void unregister(@NotNull Class<T> type) {
        ServiceRegistration<?> found = null;
        synchronized (lock) {
            List<ServiceRegistration<?>> entries = providers.get(type);
            if (entries != null) {
                for (ServiceRegistration<?> entry : entries) {
                    if (!entry.isClosed() && entry.owner == null) {
                        found = entry;
                        break;
                    }
                }
            }
        }
        if (found != null) {
            found.close();
        }
    }

    /** A view whose registrations belong to {@code owner} and end with the view. */
    public @NotNull ServiceScope ownedBy(@NotNull PluginId owner) {
        return new OwnedServices(this, owner);
    }

    private <T> void add(PluginId owner, Class<T> type, T service, int priority) {
        synchronized (lock) {
            if (closed.get()) {
                throw new IllegalStateException("ServiceManager is closed");
            }
            if (!type.isInstance(service)) {
                throw new IllegalArgumentException(service.getClass().getName() + " does not implement " + type.getName());
            }
            List<ServiceRegistration<?>> owned = owner == null ? null : registrationsByOwner.get(owner);
            if (owner != null && owned == null) {
                owned = new ArrayList<>();
                registrationsByOwner.put(owner, owned);
            }
            if (hasDuplicate(owner, owned, type)) {
                throw new IllegalStateException((owner == null ? "pnLibrary runtime" : "Plugin " + owner)
                    + " already provides service " + type.getName());
            }
            ServiceRegistration<T> registration =
                new ServiceRegistration<>(this, owner, type, service, priority, sequence.getAndIncrement());
            if (owned != null) {
                owned.add(registration);
            }
            List<ServiceRegistration<?>> entries = providers.get(type);
            if (entries == null) {
                entries = new ArrayList<>();
                providers.put(type, entries);
            }
            entries.add(registration);
        }
    }

    private boolean hasDuplicate(PluginId owner, List<ServiceRegistration<?>> owned, Class<?> type) {
        List<ServiceRegistration<?>> candidates = owner != null ? owned : providers.get(type);
        if (candidates == null) {
            return false;
        }
        for (ServiceRegistration<?> candidate : candidates) {
            boolean sameScope = owner != null ? candidate.type == type : candidate.owner == null;
            if (!candidate.isClosed() && sameScope) {
                return true;
            }
        }
        return false;
    }

    @Override
    public <T> @Nullable T get(@NotNull Class<T> type) {
        List<ServiceRegistration<T>> found = registrations(type);
        return found.isEmpty() ? null : found.get(0).service;
    }

    @Override
    public <T> @NotNull List<T> all(@NotNull Class<T> type) {
        List<T> services = new ArrayList<>();
        for (ServiceRegistration<T> registration : registrations(type)) {
            services.add(registration.service);
        }
        return Collections.unmodifiableList(services);
    }

    @Override
    public @NotNull Registry<Object> registry() {
        return new ListRegistry<>("services", () -> {
            List<ServiceRegistration<?>> current = new ArrayList<>();
            synchronized (lock) {
                for (List<ServiceRegistration<?>> entries : providers.values()) {
                    current.addAll(entries);
                }
            }
            List<ServiceRegistration<?>> open = new ArrayList<>();
            for (ServiceRegistration<?> registration : current) {
                if (!registration.isClosed()) {
                    open.add(registration);
                }
            }
            Collections.sort(open, new Comparator<ServiceRegistration<?>>() {
                @Override
                public int compare(ServiceRegistration<?> a, ServiceRegistration<?> b) {
                    int byType = a.type.getName().compareTo(b.type.getName());
                    if (byType != 0) {
                        return byType;
                    }
                    int byPriority = Integer.compare(b.priority, a.priority);
                    return byPriority != 0 ? byPriority : Long.compare(a.order, b.order);
                }
            });
            List<RegistryEntry<Object>> entries = new ArrayList<>();
            for (ServiceRegistration<?> registration : open) {
                String owner = registration.owner == null ? "pnLibrary" : registration.owner.toString();
                entries.add(new RegistryEntry<Object>(registration.type.getName(), registration.service, owner));
            }
            return entries;
        });
    }

    void unregisterAll(PluginId owner) {
        List<ServiceRegistration<?>> registrations;
        synchronized (lock) {
            List<ServiceRegistration<?>> owned = registrationsByOwner.get(owner);
            registrations = owned == null ? new ArrayList<ServiceRegistration<?>>() : new ArrayList<>(owned);
        }
        for (ServiceRegistration<?> registration : registrations) {
            registration.close();
        }
    }

    /** Removes the service the plugin registered under {@code type}. */
    public <T> void unregister(@NotNull PluginId owner, @NotNull Class<T> type) {
        ServiceRegistration<?> found = null;
        synchronized (lock) {
            List<ServiceRegistration<?>> owned = registrationsByOwner.get(owner);
            if (owned != null) {
                for (ServiceRegistration<?> entry : owned) {
                    if (!entry.isClosed() && entry.type == type) {
                        found = entry;
                        break;
                    }
                }
            }
        }
        if (found != null) {
            found.close();
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        List<ServiceRegistration<?>> registrations = new ArrayList<>();
        synchronized (lock) {
            for (List<ServiceRegistration<?>> entries : providers.values()) {
                registrations.addAll(entries);
            }
        }
        for (ServiceRegistration<?> registration : registrations) {
            registration.close();
        }
        synchronized (lock) {
            providers.clear();
            registrationsByOwner.clear();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> List<ServiceRegistration<T>> registrations(Class<T> type) {
        if (closed.get()) {
            throw new IllegalStateException("ServiceManager is closed");
        }
        List<ServiceRegistration<T>> current = new ArrayList<>();
        synchronized (lock) {
            List<ServiceRegistration<?>> entries = providers.get(type);
            if (entries != null) {
                for (ServiceRegistration<?> entry : entries) {
                    current.add((ServiceRegistration<T>) entry);
                }
            }
        }
        List<ServiceRegistration<T>> open = new ArrayList<>();
        for (ServiceRegistration<T> registration : current) {
            if (!registration.isClosed()) {
                open.add(registration);
            }
        }
        Collections.sort(open, new Comparator<ServiceRegistration<T>>() {
            @Override
            public int compare(ServiceRegistration<T> a, ServiceRegistration<T> b) {
                int byPriority = Integer.compare(b.priority, a.priority);
                return byPriority != 0 ? byPriority : Long.compare(a.order, b.order);
            }
        });
        return open;
    }

    Object lock() {
        return lock;
    }

    void forget(ServiceRegistration<?> registration) {
        synchronized (lock) {
            List<ServiceRegistration<?>> entries = providers.get(registration.type);
            if (entries != null) {
                entries.remove(registration);
                if (entries.isEmpty()) {
                    providers.remove(registration.type);
                }
            }
            PluginId owner = registration.owner;
            List<ServiceRegistration<?>> owned = owner == null ? null : registrationsByOwner.get(owner);
            if (owned != null) {
                owned.remove(registration);
                if (owned.isEmpty()) {
                    registrationsByOwner.remove(owner);
                }
            }
        }
    }
}
