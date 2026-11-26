package org.pnfolder.pnlibrary.core.events;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.events.Event;
import org.pnfolder.pnlibrary.api.events.inspect.EventInfo;
import org.pnfolder.pnlibrary.api.events.inspect.EventInspector;
import org.pnfolder.pnlibrary.api.events.inspect.ListenerInfo;
import org.pnfolder.pnlibrary.api.events.inspect.history.EventDay;
import org.pnfolder.pnlibrary.api.events.inspect.history.EventHour;
import org.pnfolder.pnlibrary.api.events.inspect.trace.EventTrace;
import org.pnfolder.pnlibrary.common.concurrent.ManagedWorker;
import org.pnfolder.pnlibrary.core.events.inspect.EventCounters;
import org.pnfolder.pnlibrary.core.events.inspect.history.EventHistory;
import org.pnfolder.pnlibrary.core.events.inspect.trace.EventTracer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** What administrators can look at: listeners, event types, counters, history and traces. */
final class EventInspection implements EventInspector {
    private static final Comparator<ListenerSubscription> BY_EVENT_PRIORITY_ORDER = new Comparator<ListenerSubscription>() {
        @Override
        public int compare(ListenerSubscription left, ListenerSubscription right) {
            int byName = left.getEventType().getName().compareTo(right.getEventType().getName());
            if (byName != 0) {
                return byName;
            }
            int byPriority = Integer.compare(left.getPriority(), right.getPriority());
            return byPriority != 0 ? byPriority : Long.compare(left.getOrder(), right.getOrder());
        }
    };

    private final CopyOnWriteArrayList<ListenerSubscription> subscriptions;
    private final HandlerCache<ListenerSubscription> handlers;
    private final EventCounters counters;
    private final EventHistory history;
    private final ManagedWorker worker;
    private final EventTracer tracer;

    EventInspection(
            CopyOnWriteArrayList<ListenerSubscription> subscriptions,
            HandlerCache<ListenerSubscription> handlers,
            EventCounters counters,
            @Nullable EventHistory history,
            ManagedWorker worker,
            EventTracer tracer) {
        this.subscriptions = subscriptions;
        this.handlers = handlers;
        this.counters = counters;
        this.history = history;
        this.worker = worker;
        this.tracer = tracer;
    }

    @NotNull
    @Override
    public List<ListenerInfo> listeners(@NotNull Class<? extends Event> eventType) {
        List<ListenerInfo> found = new ArrayList<ListenerInfo>();
        for (ListenerSubscription subscription : handlers.handlersFor(eventType)) {
            if (!subscription.isClosed()) {
                found.add(subscription.info());
            }
        }
        return found;
    }

    @NotNull
    @Override
    public List<ListenerInfo> listeners() {
        List<ListenerSubscription> open = new ArrayList<ListenerSubscription>();
        for (ListenerSubscription subscription : subscriptions) {
            if (!subscription.isClosed()) {
                open.add(subscription);
            }
        }
        Collections.sort(open, BY_EVENT_PRIORITY_ORDER);
        List<ListenerInfo> found = new ArrayList<ListenerInfo>();
        for (ListenerSubscription subscription : open) {
            found.add(subscription.info());
        }
        return found;
    }

    @Nullable
    @Override
    public Class<? extends Event> eventType(@NotNull String name) {
        Class<?> type = null;
        for (ListenerSubscription subscription : subscriptions) {
            if (!subscription.isClosed() && subscription.getEventType().getName().equals(name)) {
                type = subscription.getEventType();
                break;
            }
        }
        if (type == null) {
            type = counters.type(name);
        }
        return type == null ? null : type.asSubclass(Event.class);
    }

    @NotNull
    @Override
    public List<EventInfo> events() {
        Map<String, Class<?>> types = new LinkedHashMap<String, Class<?>>();
        for (ListenerSubscription subscription : subscriptions) {
            if (!subscription.isClosed() && !types.containsKey(subscription.getEventType().getName())) {
                types.put(subscription.getEventType().getName(), subscription.getEventType());
            }
        }
        for (Map.Entry<String, Class<?>> counted : counters.types().entrySet()) {
            if (types.get(counted.getKey()) == null) {
                types.put(counted.getKey(), counted.getValue());
            }
        }
        List<EventInfo> found = new ArrayList<EventInfo>();
        for (Map.Entry<String, Class<?>> entry : types.entrySet()) {
            found.add(counters.info(entry.getKey(), openListeners(entry.getValue())));
        }
        Collections.sort(found, (left, right) -> left.getEventType().compareTo(right.getEventType()));
        return found;
    }

    private int openListeners(@Nullable Class<?> type) {
        if (type == null) {
            return 0;
        }
        int count = 0;
        for (ListenerSubscription subscription : handlers.handlersFor(type)) {
            if (!subscription.isClosed()) {
                count++;
            }
        }
        return count;
    }

    @NotNull
    @Override
    public List<EventDay> history(@Nullable String eventType) {
        return history == null ? Collections.emptyList() : history.entries(eventType);
    }

    @NotNull
    @Override
    public List<EventHour> hourly(@Nullable String eventType) {
        return history == null ? Collections.emptyList() : history.hourly(eventType);
    }

    @Override
    public int asyncQueued() {
        return worker.queued();
    }

    @Nullable
    @Override
    public Path exportHistoryCsv() {
        return history == null ? null : history.exportCsv();
    }

    @Nullable
    @Override
    public Path exportHistoryJson() {
        return history == null ? null : history.exportJson();
    }

    @Override
    public int getHistoryDays() {
        return history == null ? 0 : history.getRetention();
    }

    @Override
    public void enableTracing(@NotNull Class<? extends Event> eventType, int capacity) {
        tracer.enable(eventType, capacity);
    }

    @Override
    public void disableTracing(@NotNull Class<? extends Event> eventType) {
        tracer.disable(eventType);
    }

    @NotNull
    @Override
    public List<String> tracedEvents() {
        return tracer.tracedNames();
    }

    @NotNull
    @Override
    public List<EventTrace> traces() {
        return tracer.traces();
    }

    @Override
    public void clearTraces() {
        tracer.clear();
    }
}
