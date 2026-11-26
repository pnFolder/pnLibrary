package org.pnfolder.pnlibrary.config.yaml;

import java.util.Objects;
import java.util.Set;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Selected implementation and its serialized discriminator identity. */
final class ResolvedConfigType {
    private static final String NAMESPACE_SEPARATOR = "::";

    private final String owner;
    private final Class<?> implementation;
    private final String name;
    private final Set<String> aliases;
    private final int priority;

    /**
     * Creates a declaration.
     *
     * @param owner plugin that published the type, or {@code null} for a type declared by annotation
     * @param implementation concrete class created for this declaration
     * @param name canonical discriminator value
     * @param aliases additional accepted discriminator values
     * @param priority tie-breaker when several declarations match
     */
    ResolvedConfigType(@Nullable String owner, @NotNull Class<?> implementation, @NotNull String name,
                       @NotNull Set<String> aliases, int priority) {
        this.owner = owner;
        this.implementation = implementation;
        this.name = name;
        this.aliases = aliases;
        this.priority = priority;
    }

    @Nullable
    String getOwner() {
        return owner;
    }

    @NotNull
    Class<?> getImplementation() {
        return implementation;
    }

    @NotNull
    String getName() {
        return name;
    }

    int getPriority() {
        return priority;
    }

    /**
     * Discriminator written to the file. A word that reaches a file is already visible there, so it is written plainly;
     * the {@code owner::name} form is still accepted when reading, to tell two additions with one word apart.
     */
    @NotNull
    String serializedName() {
        return name;
    }

    /** Whether {@code value} resolves this declaration: either the plain word or {@code owner::word} of its owner. */
    boolean matches(@NotNull String value) {
        int separator = value.indexOf(NAMESPACE_SEPARATOR);
        if (separator >= 0) {
            if (owner == null || !owner.equalsIgnoreCase(value.substring(0, separator))) {
                return false;
            }
            return matchesName(value.substring(separator + NAMESPACE_SEPARATOR.length()));
        }
        return matchesName(value);
    }

    private boolean matchesName(String value) {
        if (name.equalsIgnoreCase(value)) {
            return true;
        }
        for (String alias : aliases) {
            if (alias.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ResolvedConfigType)) {
            return false;
        }
        ResolvedConfigType that = (ResolvedConfigType) other;
        return priority == that.priority && Objects.equals(owner, that.owner) && implementation.equals(that.implementation)
                && name.equals(that.name) && aliases.equals(that.aliases);
    }

    @Override
    public int hashCode() {
        return Objects.hash(owner, implementation, name, aliases, priority);
    }
}
