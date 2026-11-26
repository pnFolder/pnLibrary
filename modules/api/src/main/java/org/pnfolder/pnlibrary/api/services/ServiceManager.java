package org.pnfolder.pnlibrary.api.services;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.registry.Registry;

import java.util.List;

/**
 * Registry of typed services shared by pnLibrary consumers.
 *
 * <p>Providers are selected by descending priority. When priorities are equal, the provider registered first is
 * selected first. A plugin normally obtains an owner-bound manager from its {@code ModuleContext}; those
 * registrations are removed together when the context closes.
 *
 * <p>Implementations are safe for concurrent registration, lookup, removal, and owner shutdown. Lookups observe a
 * complete registry state and {@link #all} returns a detached immutable snapshot.
 *
 * <pre>{@code
 * context.getServices().register(ChatFormatter.class, formatter, 100);
 * ChatFormatter active = context.getServices().require(ChatFormatter.class);
 * List<ChatFormatter> alternatives = context.getServices().all(ChatFormatter.class);
 * }</pre>
 */
@ApiStatus.NonExtendable
public interface ServiceManager {
    /**
     * Registers {@code service} as a provider of {@code type}.
     *
     * <p>A manager accepts at most one active provider of a given type from the same owner. The supplied object
     * must be an instance of {@code type}.
     *
     * @param type public service contract used for lookup
     * @param service implementation to register
     * @param priority selection priority; larger values are preferred
     * @throws IllegalArgumentException when {@code service} does not implement {@code type}
     * @throws IllegalStateException when the same owner already provides {@code type}
     */
    <T> void register(@NotNull Class<T> type, @NotNull T service, int priority);

    /** Registers {@code service} with priority {@code 0}; see {@link #register(Class, Object, int)}. */
    default <T> void register(@NotNull Class<T> type, @NotNull T service) {
        register(type, service, 0);
    }

    /** Removes this manager's provider of {@code type}, if present. */
    <T> void unregister(@NotNull Class<T> type);

    /** Returns the highest-priority active provider of {@code type}, or {@code null} when none exists. */
    @Nullable
    <T> T get(@NotNull Class<T> type);

    /**
     * Returns the highest-priority active provider of {@code type}.
     *
     * @throws IllegalStateException when no provider is available
     */
    @NotNull
    default <T> T require(@NotNull Class<T> type) {
        T service = get(type);
        if (service == null) {
            throw new IllegalStateException("Service " + type.getName() + " is not available");
        }
        return service;
    }

    /** Returns an immutable snapshot of active providers ordered by priority and registration time. */
    @NotNull
    <T> List<T> all(@NotNull Class<T> type);

    /** Every registered service, best first, found by the name of its type; each entry names the plugin that provides it. */
    @NotNull
    Registry<Object> registry();
}
