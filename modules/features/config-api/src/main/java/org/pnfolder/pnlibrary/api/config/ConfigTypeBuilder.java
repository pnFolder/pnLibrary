package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Fluent publication of one polymorphic configuration type, created by {@link ConfigScope#type}.
 *
 * <p>Only {@link #name} is required. Without {@code into} the word works in the configurations of the module that
 * registers it. With {@code into} it works where the address points, for example in another plugin's file, and it
 * lives and dies with the registering module. Nothing is published until {@link #register()} is called.
 */
public final class ConfigTypeBuilder<T> {
    private final ConfigScope scope;
    private final Class<T> baseType;
    private final Class<? extends T> implementation;
    private String name;
    private final Set<String> aliases = new LinkedHashSet<>();
    private int priority;
    private ConfigAddress target;
    private ConfigTypeConflict conflict = ConfigTypeConflict.FAIL;

    ConfigTypeBuilder(ConfigScope scope, Class<T> baseType, Class<? extends T> implementation) {
        this.scope = scope;
        this.baseType = baseType;
        this.implementation = implementation;
    }

    /** Sets the primary discriminator value. */
    @NotNull
    public ConfigTypeBuilder<T> name(@NotNull String value) {
        name = value;
        return this;
    }

    /** Adds alternative discriminator values accepted when reading. */
    @NotNull
    public ConfigTypeBuilder<T> alias(@NotNull String... values) {
        Collections.addAll(aliases, values);
        return this;
    }

    /** Sets the priority; the higher value wins when several registrations accept one identifier. */
    @NotNull
    public ConfigTypeBuilder<T> priority(int value) {
        priority = value;
        return this;
    }

    /** Makes the word work at {@code address}: a plugin, a module or one file. */
    @NotNull
    public ConfigTypeBuilder<T> into(@NotNull ConfigAddress address) {
        target = address;
        return this;
    }

    /** Short form for the address written as text: {@code *}, {@code quests}, {@code quests/main} or {@code quests/main:quests.yml}. */
    @NotNull
    public ConfigTypeBuilder<T> into(@NotNull String address) {
        return into(ConfigAddress.parse(address));
    }

    /** Makes the word work in one registered file. */
    @NotNull
    public ConfigTypeBuilder<T> into(@NotNull ManagedConfig<?> config) {
        ConfigId id = config.getId();
        if (id == null) {
            throw new IllegalArgumentException("This configuration has no identifier");
        }
        return into(id.getAddress());
    }

    /** Makes the word work in every file of the plugin {@code plugin}. */
    @NotNull
    public ConfigTypeBuilder<T> into(@NotNull PluginId plugin) {
        return into(ConfigAddress.plugin(plugin));
    }

    /** Makes the word work in every file of the module that owns {@code other}, for example another plugin's scope. */
    @NotNull
    public ConfigTypeBuilder<T> into(@NotNull ConfigScope other) {
        return into(ConfigAddress.module(other.getPlugin(), other.getModule()));
    }

    /** Makes the word work in the file that {@code info} describes, for example one found in the catalog. */
    @NotNull
    public ConfigTypeBuilder<T> into(@NotNull ConfigInfo info) {
        return into(info.getAddress());
    }

    /** Makes the word work in every file of every plugin that accepts it. */
    @NotNull
    public ConfigTypeBuilder<T> everywhere() {
        return into(ConfigAddress.everywhere());
    }

    /** Chooses what happens when the word is already taken at the same place; see {@link ConfigTypeConflict}. */
    @NotNull
    public ConfigTypeBuilder<T> onConflict(@NotNull ConfigTypeConflict policy) {
        conflict = policy;
        return this;
    }

    /** Publishes the type at each of {@code addresses} and returns one handle per place; a refused place fails the whole call. */
    @NotNull
    public List<ConfigTypeRegistration> registerEach(@NotNull Collection<ConfigAddress> addresses) {
        List<ConfigTypeRegistration> done = new ArrayList<>();
        try {
            for (ConfigAddress address : addresses) {
                done.add(into(address).register());
            }
        } catch (RuntimeException error) {
            for (ConfigTypeRegistration registration : done) {
                registration.close();
            }
            throw error;
        }
        return done;
    }

    /** Publishes the type and returns its handle. */
    @NotNull
    public ConfigTypeRegistration register() {
        if (name == null) {
            throw new IllegalArgumentException("Config type name is required: call name(...) before register()");
        }
        return scope.publishType(baseType, implementation, name, new LinkedHashSet<>(aliases), priority, target, conflict);
    }
}
