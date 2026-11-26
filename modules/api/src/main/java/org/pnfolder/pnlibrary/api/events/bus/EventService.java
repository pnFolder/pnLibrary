package org.pnfolder.pnlibrary.api.events.bus;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.events.Event;
import org.pnfolder.pnlibrary.api.events.inspect.EventInspector;
import org.pnfolder.pnlibrary.api.events.listener.EventListenerRegistration;
import org.pnfolder.pnlibrary.api.events.listener.EventSubscription;
import org.pnfolder.pnlibrary.api.events.listener.Listener;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

/**
 * Process-wide platform-independent event bus.
 *
 * <p>Use {@link #scope(PluginId)} once per {@link PluginId} and retain the returned handle. Event classes and
 * listeners depend only on {@code pnlibrary-api}, so the same code runs on Bukkit, BungeeCord, and Velocity.
 * {@link #callEvent} invokes matching listeners immediately on the calling thread and returns only after dispatch has
 * finished. This makes mutable and resulted events predictable: their final state is available as soon as the method
 * returns. Scope mutation and shutdown are safe from arbitrary threads.
 */
@ApiStatus.NonExtendable
public interface EventService extends AutoCloseable {
    /** Returns the existing plugin scope or creates it atomically. */
    @NotNull
    EventScope scope(@NotNull PluginId pluginId);

    /**
     * Returns the scope of one module. {@code pluginId} is the unique key of the scope; {@code plugin} and
     * {@code module} are the names reported by {@link #getInspector()} and {@code EventTrace} for everything
     * registered through it.
     */
    @NotNull
    EventScope scope(@NotNull PluginId pluginId, @NotNull PluginId plugin, @NotNull ModuleId module);

    /** Registers all annotated methods from {@code listener} under {@code pluginId}. */
    @NotNull
    default EventListenerRegistration register(@NotNull PluginId pluginId, @NotNull Listener listener) {
        return scope(pluginId).register(listener);
    }

    /** Registers one typed listener under {@code pluginId} using normal priority. */
    @NotNull
    default <E extends Event> EventSubscription subscribe(
            @NotNull PluginId pluginId, @NotNull Class<E> eventType, @NotNull Consumer<E> listener) {
        return scope(pluginId).subscribe(eventType, listener);
    }

    /** Registers one typed listener under {@code pluginId} with complete dispatch options. */
    @NotNull
    default <E extends Event> EventSubscription subscribe(
            @NotNull PluginId pluginId,
            @NotNull Class<E> eventType,
            int priority,
            boolean ignoreDenied,
            @NotNull Consumer<E> listener) {
        return scope(pluginId).subscribe(eventType, priority, ignoreDenied, listener);
    }

    /**
     * Invokes every matching listener synchronously and returns the same event instance. Listener-written fields and
     * results are final when this method returns.
     */
    @NotNull
    <E extends Event> E callEvent(@NotNull E event);

    /**
     * Dispatches {@code event} on the bus's own background thread ({@link DispatchMode#ASYNC}) and completes with the
     * same instance once every listener has run. Listeners must not touch the game world; use the overload with an
     * executor when they need to.
     */
    @NotNull
    <E extends Event> CompletionStage<E> callEventAsync(@NotNull E event);

    /**
     * Dispatches {@code event} on {@code executor} and completes with the same instance once every listener has run.
     * Listeners then run on the executor's thread, not the caller's, so pass a platform-appropriate executor (for
     * example one that hops to the server thread) when listeners touch the game world.
     */
    @NotNull
    <E extends Event> CompletionStage<E> callEventAsync(@NotNull E event, @NotNull Executor executor);

    /** Read-only view of listeners, call counters and traces of this bus. */
    @NotNull
    EventInspector getInspector();

    /** Removes and closes the scope belonging to {@code pluginId}. */
    void unregisterAll(@NotNull PluginId pluginId);

    /** Closes all scopes and prevents new subscriptions or publications. */
    @Override
    void close();
}
