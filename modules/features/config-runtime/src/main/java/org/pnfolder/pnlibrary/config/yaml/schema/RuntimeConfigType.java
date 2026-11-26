package org.pnfolder.pnlibrary.config.yaml.schema;

import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.NotNull;

/** Runtime extension of a polymorphic configuration type registry. */
public final class RuntimeConfigType {
    private final String owner;
    private final Class<?> baseType;
    private final Class<?> implementation;
    private final String name;
    private final Set<String> aliases;
    private final int priority;

    /**
     * Creates a runtime type.
     *
     * @param owner plugin namespace that registered the implementation
     * @param baseType annotated polymorphic contract
     * @param implementation concrete no-argument implementation
     * @param name canonical discriminator value
     * @param aliases additional accepted discriminator values
     * @param priority tie-breaker when compatible declarations overlap
     */
    public RuntimeConfigType(@NotNull String owner, @NotNull Class<?> baseType, @NotNull Class<?> implementation,
                             @NotNull String name, @NotNull Set<String> aliases, int priority) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.baseType = Objects.requireNonNull(baseType, "baseType");
        this.implementation = Objects.requireNonNull(implementation, "implementation");
        this.name = Objects.requireNonNull(name, "name");
        this.aliases = Objects.requireNonNull(aliases, "aliases");
        this.priority = priority;
    }

    @NotNull
    public String getOwner() {
        return owner;
    }

    @NotNull
    public Class<?> getBaseType() {
        return baseType;
    }

    @NotNull
    public Class<?> getImplementation() {
        return implementation;
    }

    @NotNull
    public String getName() {
        return name;
    }

    @NotNull
    public Set<String> getAliases() {
        return aliases;
    }

    public int getPriority() {
        return priority;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RuntimeConfigType)) {
            return false;
        }
        RuntimeConfigType that = (RuntimeConfigType) other;
        return priority == that.priority && owner.equals(that.owner) && baseType.equals(that.baseType)
                && implementation.equals(that.implementation) && name.equals(that.name) && aliases.equals(that.aliases);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, baseType, implementation, name, aliases, priority);
    }

    @Override
    public String toString() {
        return "RuntimeConfigType(owner=" + owner + ", baseType=" + baseType + ", implementation=" + implementation
                + ", name=" + name + ", aliases=" + aliases + ", priority=" + priority + ")";
    }
}
