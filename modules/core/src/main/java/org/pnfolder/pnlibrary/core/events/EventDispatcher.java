package org.pnfolder.pnlibrary.core.events;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.events.Event;
import org.pnfolder.pnlibrary.api.events.bus.DispatchMode;
import org.pnfolder.pnlibrary.api.events.bus.EventScope;
import org.pnfolder.pnlibrary.api.events.bus.EventService;
import org.pnfolder.pnlibrary.api.events.inspect.EventInspector;
import org.pnfolder.pnlibrary.api.events.inspect.trace.EventTrace;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.common.concurrent.ManagedWorker;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.events.inspect.EventCounters;
import org.pnfolder.pnlibrary.core.events.inspect.history.EventHistory;
import org.pnfolder.pnlibrary.core.events.inspect.trace.EventTracer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/** Calls events on the listeners of every plugin, in priority order, and keeps the counters of what happened. */
public final class EventDispatcher implements EventService {
    private static final int ASYNC_THREADS = 2;

    private final EventHistory history;
    private final EventErrorLogger errorLogger;
    private final Map<PluginId, PluginEventScope> scopes = new HashMap<PluginId, PluginEventScope>();
    private final CopyOnWriteArrayList<ListenerSubscription> subscriptions = new CopyOnWriteArrayList<ListenerSubscription>();
    private final AtomicLong sequence = new AtomicLong();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ManagedWorker worker = new ManagedWorker("pnLibrary-events", ASYNC_THREADS);
    private final HandlerCache<ListenerSubscription> handlers;
    private final EventTracer tracer = new EventTracer();
    private final EventCounters counters;
    private final EventInspector inspector;

    /** A dispatcher that keeps no history. */
    public EventDispatcher(@NotNull EventErrorLogger errorLogger) {
        this(null, errorLogger);
    }

    /** A dispatcher that counts calls in {@code history} and passes listener failures to {@code errorLogger}. */
    public EventDispatcher(@Nullable EventHistory history, @NotNull EventErrorLogger errorLogger) {
        this.history = history;
        this.errorLogger = errorLogger;
        this.counters = new EventCounters(history);
        this.handlers = new HandlerCache<ListenerSubscription>(
                () -> subscriptions,
                (subscription, type) -> !subscription.isClosed() && subscription.getEventType().isAssignableFrom(type),
                Comparator.comparingInt(ListenerSubscription::getPriority).thenComparingLong(ListenerSubscription::getOrder));
        this.inspector = new EventInspection(subscriptions, handlers, counters, history, worker, tracer);
    }

    @NotNull
    @Override
    public EventScope scope(@NotNull PluginId pluginId) {
        return scopeFor(pluginId, pluginId, null);
    }

    @NotNull
    @Override
    public EventScope scope(@NotNull PluginId pluginId, @NotNull PluginId plugin, @NotNull ModuleId module) {
        return scopeFor(pluginId, plugin, module);
    }

    private EventScope scopeFor(PluginId pluginId, PluginId plugin, @Nullable ModuleId module) {
        synchronized (scopes) {
            checkOpen();
            PluginEventScope scope = scopes.get(pluginId);
            if (scope == null) {
                scope = new PluginEventScope(this, pluginId, plugin, module);
                scopes.put(pluginId, scope);
            }
            return scope;
        }
    }

    @NotNull
    @Override
    public <E extends Event> E callEvent(@NotNull E event) {
        return callAs(event, DispatchMode.SYNC);
    }

    @NotNull
    @Override
    public <E extends Event> CompletionStage<E> callEventAsync(@NotNull E event) {
        return worker.submit(() -> callAs(event, DispatchMode.ASYNC));
    }

    @NotNull
    @Override
    public <E extends Event> CompletionStage<E> callEventAsync(@NotNull E event, @NotNull Executor executor) {
        return CompletableFuture.supplyAsync(() -> callAs(event, DispatchMode.ASYNC), executor);
    }

    private <E extends Event> E callAs(E event, DispatchMode mode) {
        checkOpen();
        try {
            dispatchInline(event, mode);
        } finally {
            counters.count(event);
        }
        return event;
    }

    @NotNull
    @Override
    public EventInspector getInspector() {
        return inspector;
    }

    private void dispatchInline(Event event, DispatchMode mode) {
        event.bindDispatch(mode, Thread.currentThread());
        List<ListenerSubscription> matching = handlers.handlersFor(event.getClass());
        Class<? extends Event> tracedAs = tracer.tracedAs(event.getClass());
        List<EventTrace.Step> steps = tracedAs != null ? new ArrayList<EventTrace.Step>(matching.size()) : null;
        for (int index = 0; index < matching.size(); index++) {
            ListenerSubscription subscription = matching.get(index);
            if (!subscription.isClosed()) {
                deliver(subscription, event, steps);
            }
        }
        if (tracedAs != null) {
            tracer.record(tracedAs, event, steps, mode);
        }
    }

    private void deliver(ListenerSubscription subscription, Event event, @Nullable List<EventTrace.Step> steps) {
        String before = steps != null ? tracer.resultText(event) : null;
        if (subscription.isIgnoreDenied() && !event.isAllowed()) {
            if (steps != null) {
                steps.add(step(subscription, true, false, 0, before, before));
            }
            return;
        }
        long started = System.nanoTime();
        boolean failed = false;
        subscription.beginInvocation();
        try {
            subscription.invoke(event);
        } catch (Throwable error) {
            failed = true;
            subscription.recordFailure();
            // A broken listener is logged and skipped; a dying JVM (out of memory, stack overflow) is not ours to hide.
            Failures.rethrowIfFatal(error);
            logFailure(subscription, event, error);
        } finally {
            long elapsed = System.nanoTime() - started;
            subscription.recordElapsed(elapsed);
            if (steps != null) {
                steps.add(step(subscription, false, failed, elapsed, before, tracer.resultText(event)));
            }
        }
    }

    private static EventTrace.Step step(
            ListenerSubscription subscription,
            boolean skipped,
            boolean failed,
            long nanos,
            @Nullable String before,
            @Nullable String after) {
        PluginEventScope scope = subscription.getScope();
        return new EventTrace.Step(scope.getPlugin(), scope.getModule(), subscription.getPriority(), skipped, failed, nanos,
                before, after);
    }

    // A broken logger must not cost the remaining listeners their turn.
    private void logFailure(ListenerSubscription subscription, Event event, Throwable error) {
        Failures.runCatchingNonFatal(() -> {
            errorLogger.log(subscription.getScope().getPlugin(), "[pnLibrary/events] Listener failed for " + event.getClass().getName(), error);
            return null;
        });
    }

    @Override
    public void unregisterAll(@NotNull PluginId pluginId) {
        PluginEventScope removed;
        synchronized (scopes) {
            removed = scopes.remove(pluginId);
        }
        if (removed != null) {
            removed.closeInternal();
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        List<PluginEventScope> current;
        synchronized (scopes) {
            current = new ArrayList<PluginEventScope>(scopes.values());
            scopes.clear();
        }
        for (PluginEventScope scope : current) {
            scope.closeInternal();
        }
        subscriptions.clear();
        handlers.invalidate(null);
        worker.close();
        if (history != null) {
            history.close();
        }
    }

    boolean isClosed() {
        return closed.get();
    }

    long nextOrder() {
        return sequence.getAndIncrement();
    }

    void addSubscription(ListenerSubscription subscription) {
        subscriptions.add(subscription);
        handlers.invalidate(subscription.getEventType());
    }

    void removeSubscription(ListenerSubscription subscription) {
        subscriptions.remove(subscription);
        handlers.invalidate(subscription.getEventType());
    }

    void removeScope(PluginId pluginId, PluginEventScope scope) {
        synchronized (scopes) {
            if (scopes.get(pluginId) == scope) {
                scopes.remove(pluginId);
            }
        }
    }

    private void checkOpen() {
        if (closed.get()) {
            throw new IllegalStateException("EventService is closed");
        }
    }
}
