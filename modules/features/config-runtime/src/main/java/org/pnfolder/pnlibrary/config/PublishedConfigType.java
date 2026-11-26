package org.pnfolder.pnlibrary.config;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.config.ConfigAddress;
import org.pnfolder.pnlibrary.api.config.ConfigTypeRegistration;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

/** A dynamically published polymorphic type; stays visible to codecs until {@link #close()} or its scope closes. */
final class PublishedConfigType implements ConfigTypeRegistration {
    private final Set<PublishedConfigType> registry;
    private final PluginId scopeKey;
    private final PluginId owner;
    private final Class<?> baseType;
    private final Class<?> implementation;
    private final String name;
    private final Set<String> aliases;
    private final int priority;
    private final ConfigAddress target;
    private volatile boolean active = true;

    PublishedConfigType(Set<PublishedConfigType> registry, PluginId scopeKey, PluginId owner, Class<?> baseType,
                        Class<?> implementation, String name, Set<String> aliases, int priority, ConfigAddress target) {
        this.registry = registry;
        this.scopeKey = scopeKey;
        this.owner = owner;
        this.baseType = baseType;
        this.implementation = implementation;
        this.name = name;
        this.aliases = aliases;
        this.priority = priority;
        this.target = target;
    }

    PluginId scopeKey() {
        return scopeKey;
    }

    Set<String> aliases() {
        return aliases;
    }

    int priority() {
        return priority;
    }

    Set<String> words() {
        Set<String> words = new LinkedHashSet<>(aliases);
        words.add(name);
        return Collections.unmodifiableSet(words);
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
    public Class<?> getBaseType() {
        return baseType;
    }

    @Override
    @NotNull
    public Class<?> getImplementation() {
        return implementation;
    }

    @Override
    @NotNull
    public String getName() {
        return name;
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
