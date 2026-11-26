package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

import java.util.Objects;
import java.util.Set;

/** One word that a polymorphic configuration type understands in its {@code type} field. */
public final class ConfigTypeInfo {
    private final Class<?> baseType;
    private final Class<?> implementation;
    private final String name;
    private final Set<String> aliases;
    private final PluginId owner;
    private final int priority;
    private final ConfigAddress target;

    /**
     * Creates a description of a word.
     *
     * @param baseType the polymorphic contract
     * @param implementation class created for the word
     * @param name main word
     * @param aliases other accepted words
     * @param owner plugin that added the word, or {@code null} when the base type declares it itself
     * @param priority wins over a lower priority when two additions use the same word
     * @param target where the word works, or {@code null} for a word declared by the base type
     */
    public ConfigTypeInfo(@NotNull Class<?> baseType, @NotNull Class<?> implementation, @NotNull String name,
                          @NotNull Set<String> aliases, @Nullable PluginId owner, int priority,
                          @Nullable ConfigAddress target) {
        this.baseType = Objects.requireNonNull(baseType, "baseType");
        this.implementation = Objects.requireNonNull(implementation, "implementation");
        this.name = Objects.requireNonNull(name, "name");
        this.aliases = Objects.requireNonNull(aliases, "aliases");
        this.owner = owner;
        this.priority = priority;
        this.target = target;
    }

    /** The polymorphic contract. */
    @NotNull
    public Class<?> getBaseType() {
        return baseType;
    }

    /** Class created for the word. */
    @NotNull
    public Class<?> getImplementation() {
        return implementation;
    }

    /** Main word. */
    @NotNull
    public String getName() {
        return name;
    }

    /** Other accepted words. */
    @NotNull
    public Set<String> getAliases() {
        return aliases;
    }

    /** Plugin that added the word, or {@code null} when the base type declares it itself. */
    @Nullable
    public PluginId getOwner() {
        return owner;
    }

    /** Wins over a lower priority when two additions use the same word. */
    public int getPriority() {
        return priority;
    }

    /** Where the word works, or {@code null} for a word declared by the base type. */
    @Nullable
    public ConfigAddress getTarget() {
        return target;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ConfigTypeInfo)) {
            return false;
        }
        ConfigTypeInfo that = (ConfigTypeInfo) other;
        return priority == that.priority && baseType.equals(that.baseType) && implementation.equals(that.implementation)
                && name.equals(that.name) && aliases.equals(that.aliases) && Objects.equals(owner, that.owner)
                && Objects.equals(target, that.target);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseType, implementation, name, aliases, owner, priority, target);
    }

    @Override
    public String toString() {
        return "ConfigTypeInfo(baseType=" + baseType + ", implementation=" + implementation + ", name=" + name
                + ", aliases=" + aliases + ", owner=" + owner + ", priority=" + priority + ", target=" + target + ")";
    }
}
