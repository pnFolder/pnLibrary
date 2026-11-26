package org.pnfolder.pnlibrary.core.events;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.events.Event;
import org.pnfolder.pnlibrary.api.events.inspect.ListenerInfo;
import org.pnfolder.pnlibrary.api.events.listener.EventSubscription;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Consumer;

/** One listener of one event type, with the counters of how it has been doing. */
final class ListenerSubscription implements EventSubscription {
    private final EventDispatcher dispatcher;
    private final PluginEventScope scope;
    private final Class<? extends Event> eventType;
    private final int priority;
    private final boolean ignoreDenied;
    private final Consumer<Event> listener;
    private final long order;
    private final String source;
    private final LongAdder invocations = new LongAdder();
    private final LongAdder failures = new LongAdder();
    private final LongAdder totalNanos = new LongAdder();
    private final AtomicLong maxNanos = new AtomicLong();
    private final AtomicBoolean subscriptionClosed = new AtomicBoolean(false);

    @SuppressWarnings("unchecked")
    <E extends Event> ListenerSubscription(
            EventDispatcher dispatcher,
            PluginEventScope scope,
            Class<E> eventType,
            int priority,
            boolean ignoreDenied,
            Consumer<E> listener,
            long order,
            String source) {
        this.dispatcher = dispatcher;
        this.scope = scope;
        this.eventType = eventType;
        this.priority = priority;
        this.ignoreDenied = ignoreDenied;
        this.listener = (Consumer<Event>) (Consumer<?>) listener;
        this.order = order;
        this.source = source;
    }

    PluginEventScope getScope() {
        return scope;
    }

    Class<? extends Event> getEventType() {
        return eventType;
    }

    int getPriority() {
        return priority;
    }

    boolean isIgnoreDenied() {
        return ignoreDenied;
    }

    long getOrder() {
        return order;
    }

    void beginInvocation() {
        invocations.increment();
    }

    void recordFailure() {
        failures.increment();
    }

    void recordElapsed(long nanos) {
        totalNanos.add(nanos);
        maxNanos.accumulateAndGet(nanos, Math::max);
    }

    @NotNull
    ListenerInfo info() {
        return new ListenerInfo(scope.getPlugin(), scope.getModule(), eventType.getName(), priority, ignoreDenied, source,
                invocations.sum(), failures.sum(), totalNanos.sum(), maxNanos.get());
    }

    void invoke(Event event) {
        listener.accept(eventType.cast(event));
    }

    @Override
    public boolean isClosed() {
        return subscriptionClosed.get();
    }

    @Override
    public void close() {
        if (!subscriptionClosed.compareAndSet(false, true)) {
            return;
        }
        dispatcher.removeSubscription(this);
        scope.remove(this);
    }
}
