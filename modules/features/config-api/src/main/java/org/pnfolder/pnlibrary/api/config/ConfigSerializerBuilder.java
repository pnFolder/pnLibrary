package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

/**
 * Fluent publication of one serializer, created by {@link ConfigScope#serializerFor}.
 *
 * <p>Without {@code into} the serializer works in the files of the module that publishes it. With {@code into} it
 * works where the address points, for example in another plugin's files, and it lives and dies with the publishing
 * module. Nothing is published until {@link #register()} is called.
 */
public final class ConfigSerializerBuilder<T> {
    private final ConfigScope scope;
    private final Class<T> type;
    private final ConfigSerializer<T> serializer;
    private int priority;
    private ConfigAddress target;

    ConfigSerializerBuilder(ConfigScope scope, Class<T> type, ConfigSerializer<T> serializer) {
        this.scope = scope;
        this.type = type;
        this.serializer = serializer;
    }

    /** Sets the priority; the higher value wins when several additions handle one type. */
    @NotNull
    public ConfigSerializerBuilder<T> priority(int value) {
        priority = value;
        return this;
    }

    /** Makes the serializer work at {@code address}: everything, a plugin, a module or one file. */
    @NotNull
    public ConfigSerializerBuilder<T> into(@NotNull ConfigAddress address) {
        target = address;
        return this;
    }

    /** Short form for the address written as text: {@code *}, {@code quests}, {@code quests/main} or {@code quests/main:quests.yml}. */
    @NotNull
    public ConfigSerializerBuilder<T> into(@NotNull String address) {
        return into(ConfigAddress.parse(address));
    }

    /** Makes the serializer work in one registered file. */
    @NotNull
    public ConfigSerializerBuilder<T> into(@NotNull ManagedConfig<?> config) {
        ConfigId id = config.getId();
        if (id == null) {
            throw new IllegalArgumentException("This configuration has no identifier");
        }
        return into(id.getAddress());
    }

    /** Makes the serializer work in every file of the plugin {@code plugin}. */
    @NotNull
    public ConfigSerializerBuilder<T> into(@NotNull PluginId plugin) {
        return into(ConfigAddress.plugin(plugin));
    }

    /** Makes the serializer work in every file of the module that owns {@code other}, for example another plugin's scope. */
    @NotNull
    public ConfigSerializerBuilder<T> into(@NotNull ConfigScope other) {
        return into(ConfigAddress.module(other.getPlugin(), other.getModule()));
    }

    /** Makes the serializer work in the file that {@code info} describes, for example one found in the catalog. */
    @NotNull
    public ConfigSerializerBuilder<T> into(@NotNull ConfigInfo info) {
        return into(info.getAddress());
    }

    /** Makes the serializer work in every file of every plugin. */
    @NotNull
    public ConfigSerializerBuilder<T> everywhere() {
        return into(ConfigAddress.everywhere());
    }

    /** Publishes the serializer and returns its handle. */
    @NotNull
    public ConfigSerializerRegistration register() {
        return scope.publishSerializer(type, serializer, priority, target);
    }
}
