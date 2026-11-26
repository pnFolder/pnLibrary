package org.pnfolder.pnlibrary.api.events.listener;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.events.Event;
import org.pnfolder.pnlibrary.api.events.bus.EventScope;

/**
 * Fluent way to subscribe to one event type; start it with {@code EventScope.on(Class)}.
 *
 * <pre>{@code
 * scope.on(PurchaseEvent.class)
 *     .priority(EventPriority.HIGH)
 *     .ignoreDenied()
 *     .listen(event -> { });
 * }</pre>
 *
 * <p>Nothing is registered until {@link #listen} is called. The builder itself holds no subscription.
 */
public final class EventListenerBuilder<E extends Event> {
    private final EventScope scope;
    private final Class<E> eventType;
    private int priority = EventPriority.NORMAL;
    private boolean ignoreDenied;

    /** Creates a builder for {@code eventType} in {@code scope}; prefer {@code EventScope.on(Class)}. */
    public EventListenerBuilder(@NotNull EventScope scope, @NotNull Class<E> eventType) {
        this.scope = scope;
        this.eventType = eventType;
    }

    /** Sets the dispatch order; lower numbers run first. Defaults to {@link EventPriority#NORMAL}. */
    @NotNull
    public EventListenerBuilder<E> priority(int value) {
        priority = value;
        return this;
    }

    /** Skips this listener when the event is already denied ({@code Event.isAllowed()} is {@code false}). Off by default. */
    @NotNull
    public EventListenerBuilder<E> ignoreDenied() {
        ignoreDenied = true;
        return this;
    }

    /** Registers {@code listener} with the configured options and returns the live subscription. */
    @NotNull
    public EventSubscription listen(@NotNull Consumer<E> listener) {
        return scope.subscribe(eventType, priority, ignoreDenied, listener);
    }

    /**
     * Registers {@code listener} to run for the first matching event only, then removes it. Closing the returned
     * subscription before that cancels it.
     */
    @NotNull
    public EventSubscription listenOnce(@NotNull final Consumer<E> listener) {
        final AtomicBoolean fired = new AtomicBoolean();
        final AtomicReference<EventSubscription> self = new AtomicReference<EventSubscription>();
        EventSubscription subscription = scope.subscribe(eventType, priority, ignoreDenied, event -> {
            if (fired.compareAndSet(false, true)) {
                EventSubscription current = self.get();
                if (current != null) {
                    current.close();
                }
                listener.accept(event);
            }
        });
        self.set(subscription);
        if (fired.get()) {
            subscription.close();
        }
        return subscription;
    }
}
