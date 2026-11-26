package org.pnfolder.pnlibrary.api.expansions;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.registry.Registry;

import java.util.ArrayList;
import java.util.List;

/**
 * Registry that owns the lifecycle of {@link Expansion}s: it enables them on registration and disables them in
 * reverse order when the library stops.
 *
 * <p>A failing expansion is reported to the log and left {@link ExpansionState#FAILED}; it never prevents the others
 * from starting or stopping. Implementations are safe for concurrent use.
 *
 * <pre>{@code
 * AutoCloseable handle = library.getExpansions().register(new WeatherExpansion());
 * WeatherExpansion weather = library.getExpansions().get(WeatherExpansion.class);
 * handle.close(); // disables and unregisters
 * }</pre>
 */
@ApiStatus.NonExtendable
public interface ExpansionManager {
    /**
     * Registers and enables {@code expansion}.
     *
     * @return a handle that unregisters the expansion and disables it
     * @throws IllegalStateException when an expansion with the same {@link Expansion#getId} is registered
     */
    @NotNull
    AutoCloseable register(@NotNull Expansion expansion);

    /** Returns the registered expansion with {@code id}, or {@code null}. */
    @Nullable
    Expansion get(@NotNull String id);

    /** Returns the first registered expansion that is an instance of {@code type}, or {@code null}. */
    @Nullable
    <T extends Expansion> T get(@NotNull Class<T> type);

    /** Returns an immutable snapshot of the registered expansions in registration order. */
    @NotNull
    List<Expansion> all();

    /** Returns the registered expansion called {@code id}, or fails naming the registered ones. */
    @NotNull
    default Expansion require(@NotNull String id) {
        Expansion expansion = get(id);
        if (expansion != null) {
            return expansion;
        }
        List<String> ids = new ArrayList<>();
        for (Expansion registered : all()) {
            ids.add(registered.getId());
        }
        throw new IllegalStateException("Expansion '" + id + "' is not registered; registered: " + String.join(", ", ids));
    }

    /** The same expansions as {@link #all}, as a {@link Registry}: found by id, owned by the library. */
    @NotNull
    Registry<Expansion> registry();
}
