package org.pnfolder.pnlibrary.core.events.inspect.trace;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.events.Event;
import org.pnfolder.pnlibrary.api.events.ResultedEvent;
import org.pnfolder.pnlibrary.api.events.bus.DispatchMode;
import org.pnfolder.pnlibrary.api.events.inspect.trace.EventTrace;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Keeps the recorded calls of the events an administrator switched tracing on for. */
public final class EventTracer {
    private static final Comparator<EventTrace> OLDEST_FIRST = new Comparator<EventTrace>() {
        @Override
        public int compare(EventTrace left, EventTrace right) {
            Instant a = left.getAt();
            Instant b = right.getAt();
            if (a == null || b == null) {
                return a == b ? 0 : a == null ? -1 : 1;
            }
            return a.compareTo(b);
        }
    };

    /** Traced event types and their capacity; empty almost always, so dispatch only reads one volatile field. */
    private volatile Map<Class<? extends Event>, Integer> traced = Collections.emptyMap();

    /** Calls per traced type, so a type with a small capacity never pushes out the calls of another. */
    private final Map<Class<? extends Event>, ArrayDeque<EventTrace>> recorded =
            new HashMap<Class<? extends Event>, ArrayDeque<EventTrace>>();

    /** Starts recording calls of {@code eventType} and its subtypes, keeping the latest {@code capacity} of them. */
    public void enable(@NotNull Class<? extends Event> eventType, int capacity) {
        if (capacity < 1 || capacity > 1_000) {
            throw new IllegalArgumentException("Trace capacity must be between 1 and 1000");
        }
        synchronized (recorded) {
            Map<Class<? extends Event>, Integer> next = new HashMap<Class<? extends Event>, Integer>(traced);
            next.put(eventType, capacity);
            traced = next;
        }
    }

    /** Stops recording {@code eventType}; calls already recorded stay until {@link #clear()}. */
    public void disable(@NotNull Class<? extends Event> eventType) {
        synchronized (recorded) {
            Map<Class<? extends Event>, Integer> next = new HashMap<Class<? extends Event>, Integer>(traced);
            next.remove(eventType);
            traced = next;
        }
    }

    /** The names of the traced event types, sorted. */
    @NotNull
    public List<String> tracedNames() {
        List<String> names = new ArrayList<String>();
        for (Class<? extends Event> type : traced.keySet()) {
            names.add(type.getName());
        }
        Collections.sort(names);
        return names;
    }

    /** Every recorded call, oldest first. */
    @NotNull
    public List<EventTrace> traces() {
        List<EventTrace> all = new ArrayList<EventTrace>();
        synchronized (recorded) {
            for (ArrayDeque<EventTrace> calls : recorded.values()) {
                all.addAll(calls);
            }
        }
        Collections.sort(all, OLDEST_FIRST);
        return all;
    }

    /** Forgets every recorded call; tracing stays on. */
    public void clear() {
        synchronized (recorded) {
            recorded.clear();
        }
    }

    /** The traced type that {@code type} is recorded under, or {@code null} when it is not traced. */
    @Nullable
    public Class<? extends Event> tracedAs(@NotNull Class<?> type) {
        Map<Class<? extends Event>, Integer> current = traced;
        if (current.isEmpty()) {
            return null;
        }
        for (Class<? extends Event> candidate : current.keySet()) {
            if (candidate.isAssignableFrom(type)) {
                return candidate;
            }
        }
        return null;
    }

    /** The result of a resulted {@code event} as text, or {@code null} for a plain event. */
    @Nullable
    public String resultText(@NotNull Event event) {
        if (!(event instanceof ResultedEvent)) {
            return null;
        }
        Object result = ((ResultedEvent<?>) event).getResult();
        return result == null ? null : result.toString();
    }

    /** Stores one call of {@code event} under {@code tracedAs}, dropping the oldest calls above the capacity. */
    public void record(
            @NotNull Class<? extends Event> tracedAs,
            @NotNull Event event,
            @NotNull List<EventTrace.Step> steps,
            @NotNull DispatchMode mode) {
        EventTrace trace = new EventTrace(event.getEventName(), event.isAllowed(), resultText(event), steps, mode,
                Thread.currentThread().getName(), Instant.now());
        synchronized (recorded) {
            Integer capacity = traced.get(tracedAs);
            if (capacity == null) {
                return;
            }
            ArrayDeque<EventTrace> calls = recorded.get(tracedAs);
            if (calls == null) {
                calls = new ArrayDeque<EventTrace>();
                recorded.put(tracedAs, calls);
            }
            calls.addLast(trace);
            while (calls.size() > capacity) {
                calls.removeFirst();
            }
        }
    }
}
