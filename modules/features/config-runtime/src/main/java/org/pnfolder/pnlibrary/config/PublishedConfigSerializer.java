package org.pnfolder.pnlibrary.config;

import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.config.ConfigAddress;
import org.pnfolder.pnlibrary.api.config.ConfigSerializer;
import org.pnfolder.pnlibrary.api.config.ConfigSerializerRegistration;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

/** A published serializer; used by codecs until {@link #close()} or its scope closes. */
final class PublishedConfigSerializer implements ConfigSerializerRegistration {
    private final Set<PublishedConfigSerializer> registry;
    private final PluginId scopeKey;
    private final PluginId owner;
    private final Class<?> type;
    private final ConfigSerializer<?> serializer;
    private final int priority;
    private final ConfigAddress target;
    private volatile boolean active = true;

    PublishedConfigSerializer(Set<PublishedConfigSerializer> registry, PluginId scopeKey, PluginId owner, Class<?> type,
                              ConfigSerializer<?> serializer, int priority, ConfigAddress target) {
        this.registry = registry;
        this.scopeKey = scopeKey;
        this.owner = owner;
        this.type = type;
        this.serializer = serializer;
        this.priority = priority;
        this.target = target;
    }

    PluginId scopeKey() {
        return scopeKey;
    }

    ConfigSerializer<?> serializer() {
        return serializer;
    }

    void deactivate() {
        active = false;
    }

    @Override
    @NotNull
    public PluginId getOwner() {
        return owner;
    }

    @Override
    @NotNull
    public Class<?> getType() {
        return type;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    @Override
    @NotNull
    public ConfigAddress getTarget() {
        return target;
    }

    @Override
    public boolean isActive() {
        return active;
    }

    @Override
    public void close() {
        deactivate();
        synchronized (registry) {
            registry.remove(this);
        }
    }
}
