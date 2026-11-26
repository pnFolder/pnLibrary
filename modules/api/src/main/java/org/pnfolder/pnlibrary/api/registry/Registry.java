package org.pnfolder.pnlibrary.api.registry;

import java.util.List;
import java.util.NoSuchElementException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A read-only list of everything of one kind that plugins registered: placeholders, actions, formats. Every kind gives
 * it the same way, so one list, one search and one menu work for all of them.
 *
 * <pre>{@code
 * Registry<PlaceholderInfo> placeholders = library.getPlaceholders().registry();
 * placeholders.require("shop.coins");
 * }</pre>
 *
 * @param <T> the kind of thing registered
 */
@ApiStatus.NonExtendable
public interface Registry<T> {
    /** What this registry holds, for messages: {@code placeholders}. */
    @NotNull String getName();

    /** Everything registered right now, with owner and visibility. */
    @NotNull List<RegistryEntry<T>> entries();

    /** The thing named {@code key}, or {@code null} when there is none. */
    default @Nullable T get(@NotNull String key) {
        for (RegistryEntry<T> entry : entries()) {
            if (entry.getKey().equals(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** The thing named {@code key}; throws when there is none. */
    default @NotNull T require(@NotNull String key) {
        T value = get(key);
        if (value == null) {
            throw new NoSuchElementException("No '" + key + "' in " + getName());
        }
        return value;
    }

    /** Whether something is registered under {@code key}. */
    default boolean contains(@NotNull String key) {
        for (RegistryEntry<T> entry : entries()) {
            if (entry.getKey().equals(key)) {
                return true;
            }
        }
        return false;
    }
}
