package org.pnfolder.pnlibrary.core.events.inspect;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.events.Event;
import org.pnfolder.pnlibrary.api.events.inspect.EventInfo;
import org.pnfolder.pnlibrary.core.events.inspect.history.EventHistory;

import java.lang.ref.WeakReference;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/** Counts the calls and the denials of every event type, and passes each call to the history. */
public final class EventCounters {
    private final EventHistory history;
    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    /** Creates counters that also feed {@code history} when it is not {@code null}. */
    public EventCounters(@Nullable EventHistory history) {
        this.history = history;
    }

    /** Counts one call of {@code event} and passes it to the history. */
    public void count(@NotNull Event event) {
        String name = event.getClass().getName();
        Counter counter = counters.get(name);
        if (counter == null) {
            counter = counters.computeIfAbsent(name, key -> new Counter(event.getClass()));
        }
        counter.calls.increment();
        counter.lastMillis = System.currentTimeMillis();
        if (!event.isAllowed()) {
            counter.denied.increment();
        }
        if (history != null) {
            history.record(name, !event.isAllowed());
        }
    }

    /** The class of the event {@code name} if it was called and is still loaded. */
    @Nullable
    public Class<?> type(@NotNull String name) {
        Counter counter = counters.get(name);
        return counter == null ? null : counter.type.get();
    }

    /** Every counted event name with its class, or {@code null} once the class was unloaded. */
    @NotNull
    public Map<String, Class<?>> types() {
        Map<String, Class<?>> found = new LinkedHashMap<>();
        for (Map.Entry<String, Counter> entry : counters.entrySet()) {
            found.put(entry.getKey(), entry.getValue().type.get());
        }
        return found;
    }

    /** The counters of {@code name} with the number of {@code listeners} it has now. */
    @NotNull
    public EventInfo info(@NotNull String name, int listeners) {
        Counter counter = counters.get(name);
        if (counter == null) {
            return new EventInfo(name, listeners, 0, 0, null);
        }
        long last = counter.lastMillis;
        return new EventInfo(name, listeners, counter.calls.sum(), counter.denied.sum(),
                last > 0 ? Instant.ofEpochMilli(last) : null);
    }

    private static final class Counter {
        final WeakReference<Class<?>> type;
        final LongAdder calls = new LongAdder();
        final LongAdder denied = new LongAdder();
        volatile long lastMillis;

        Counter(Class<?> type) {
            this.type = new WeakReference<>(type);
        }
    }
}
