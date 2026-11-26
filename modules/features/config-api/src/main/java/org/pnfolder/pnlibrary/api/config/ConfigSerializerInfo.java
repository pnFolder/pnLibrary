package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

import java.util.Objects;

/** One serializer that plugins added, as the catalog lists it. */
public final class ConfigSerializerInfo {
    private final Class<?> type;
    private final PluginId owner;
    private final int priority;
    private final ConfigAddress target;

    /**
     * Creates a description of a serializer.
     *
     * @param type value type the serializer converts
     * @param owner plugin that published it
     * @param priority wins over a lower priority when two additions handle one type
     * @param target where it works
     */
    public ConfigSerializerInfo(@NotNull Class<?> type, @NotNull PluginId owner, int priority,
                                @NotNull ConfigAddress target) {
        this.type = type;
        this.owner = owner;
        this.priority = priority;
        this.target = target;
    }

    /** Value type the serializer converts. */
    @NotNull
    public Class<?> getType() {
        return type;
    }

    /** Plugin that published it. */
    @NotNull
    public PluginId getOwner() {
        return owner;
    }

    /** Wins over a lower priority when two additions handle one type. */
    public int getPriority() {
        return priority;
    }

    /** Where it works. */
    @NotNull
    public ConfigAddress getTarget() {
        return target;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConfigSerializerInfo)) {
            return false;
        }
        ConfigSerializerInfo that = (ConfigSerializerInfo) other;
        return priority == that.priority && type.equals(that.type) && owner.equals(that.owner)
                && target.equals(that.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, owner, priority, target);
    }

    @Override
    public String toString() {
        return "ConfigSerializerInfo(type=" + type + ", owner=" + owner + ", priority=" + priority
                + ", target=" + target + ")";
    }
}
