package org.pnfolder.pnlibrary.api.events.bus;

import java.util.function.Consumer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.events.Event;
import org.pnfolder.pnlibrary.api.events.listener.EventListenerBuilder;
import org.pnfolder.pnlibrary.api.events.listener.EventListenerRegistration;
import org.pnfolder.pnlibrary.api.events.listener.EventPriority;
import org.pnfolder.pnlibrary.api.events.listener.EventSubscription;
import org.pnfolder.pnlibrary.api.events.listener.Listener;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

/**
 * Plugin-ID-bound access to the pnLibrary event bus.
 *
 * <p>Closing the scope removes every listener registered through it.
 */
@ApiStatus.NonExtendable
public interface EventScope extends AutoCloseable {
    /** Plugin identity that owns every subscription in this scope. */
    @NotNull
    PluginId getPluginId();

    /** Whether this scope has removed its listeners and stopped accepting registrations. */
    boolean isClosed();

    /**
     * Discovers and registers every {@code @EventHandler} method on {@code listener}. Invalid handler signatures fail
     * immediately during registration.
     */
    @NotNull
    EventListenerRegistration register(@NotNull Listener listener);

    /** Registers {@code listener} for {@code eventType} using the default priority. */
    @NotNull
    default <E extends Event> EventSubscription subscribe(@NotNull Class<E> eventType, @NotNull Consumer<E> listener) {
        return subscribe(eventType, EventPriority.NORMAL, false, listener);
    }

    /** Registers {@code listener} at {@code priority}. */
    @NotNull
    default <E extends Event> EventSubscription subscribe(
            @NotNull Class<E> eventType, int priority, @NotNull Consumer<E> listener) {
        return subscribe(eventType, priority, false, listener);
    }

    /**
     * Registers a listener with complete dispatch options.
     *
     * <p>A listener also receives subclasses and implementations of {@code eventType}. Set {@code ignoreDenied} to
     * skip the listener once the event is denied ({@link Event#isAllowed()} is {@code false}).
     */
    @NotNull
    <E extends Event> EventSubscription subscribe(
            @NotNull Class<E> eventType, int priority, boolean ignoreDenied, @NotNull Consumer<E> listener);

    /**
     * Starts building a subscription to {@code eventType} in this scope.
     *
     * <pre>{@code
     * scope.on(PurchaseEvent.class).priority(EventPriority.HIGH).ignoreDenied().listen(event -> { });
     * }</pre>
     */
    @NotNull
    default <E extends Event> EventListenerBuilder<E> on(@NotNull Class<E> eventType) {
        return new EventListenerBuilder<E>(this, eventType);
    }

    /** Invokes every matching listener synchronously and returns the same event instance. */
    @NotNull
    <E extends Event> E callEvent(@NotNull E event);

    /** Removes every listener registered through this scope. Closing is idempotent. */
    @Override
    void close();
}
