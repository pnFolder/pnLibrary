package org.pnfolder.pnlibrary.api.registry;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/**
 * One thing in a {@link Registry} as an administrator sees it in lists.
 *
 * @param <T> the kind of thing registered
 */
public final class RegistryEntry<T> {
    private final String key;
    private final T value;
    private final String owner;
    private final Visibility visibility;
    private final String description;

    /**
     * @param key the name it is found by, such as {@code shop.coins}
     * @param value the registered thing itself
     * @param owner who registered it, as {@code plugin/module}, or {@code pnLibrary} for what the library brings
     * @param visibility who may use it
     * @param description what it is for, or an empty string
     */
    public RegistryEntry(@NotNull String key, @NotNull T value, @NotNull String owner,
                         @NotNull Visibility visibility, @NotNull String description) {
        this.key = Objects.requireNonNull(key, "key");
        this.value = Objects.requireNonNull(value, "value");
        this.owner = Objects.requireNonNull(owner, "owner");
        this.visibility = Objects.requireNonNull(visibility, "visibility");
        this.description = Objects.requireNonNull(description, "description");
    }

    /** An entry with {@link Visibility#ALL} and no description. */
    public RegistryEntry(@NotNull String key, @NotNull T value, @NotNull String owner) {
        this(key, value, owner, Visibility.ALL, "");
    }

    /** An entry with no description. */
    public RegistryEntry(@NotNull String key, @NotNull T value, @NotNull String owner,
                         @NotNull Visibility visibility) {
        this(key, value, owner, visibility, "");
    }

    /** The name it is found by, such as {@code shop.coins}. */
    public @NotNull String getKey() {
        return key;
    }

    /** The registered thing itself. */
    public @NotNull T getValue() {
        return value;
    }

    /** Who registered it, as {@code plugin/module}, or {@code pnLibrary} for what the library brings. */
    public @NotNull String getOwner() {
        return owner;
    }

    /** Who may use it. */
    public @NotNull Visibility getVisibility() {
        return visibility;
    }

    /** What it is for, or an empty string. */
    public @NotNull String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RegistryEntry)) {
            return false;
        }
        RegistryEntry<?> that = (RegistryEntry<?>) other;
        return key.equals(that.key) && value.equals(that.value) && owner.equals(that.owner)
            && visibility == that.visibility && description.equals(that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value, owner, visibility, description);
    }

    @Override
    public String toString() {
        return "RegistryEntry(key=" + key + ", value=" + value + ", owner=" + owner + ", visibility=" + visibility
            + ", description=" + description + ")";
    }
}
