package org.pnfolder.pnlibrary.api.events.inspect;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.events.Event;
import org.pnfolder.pnlibrary.api.events.inspect.history.EventDay;
import org.pnfolder.pnlibrary.api.events.inspect.history.EventHour;
import org.pnfolder.pnlibrary.api.events.inspect.trace.EventTrace;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

/**
 * Read-only view of the event bus: who listens, which events were called and what happened to them.
 *
 * <p>Obtained from {@code EventService.getInspector()}. Everything here works before an event has ever been called.
 *
 * <pre>{@code
 * EventInspector bus = PnLibraryProvider.get().getEvents().getInspector();
 * bus.listenerCount(PurchaseEvent.class);
 * }</pre>
 */
public interface EventInspector {
    /**
     * Lists the listeners that would run for {@code eventType}, in dispatch order. Works before the event has ever
     * been called, so it also answers who is subscribed to an event nobody has fired yet.
     */
    @NotNull
    List<ListenerInfo> listeners(@NotNull Class<? extends Event> eventType);

    /** Lists every registered listener of every plugin, ordered by event type and then dispatch order. */
    @NotNull
    List<ListenerInfo> listeners();

    /** Lists the listeners of one {@code plugin}. */
    @NotNull
    default List<ListenerInfo> listeners(@NotNull PluginId plugin) {
        return listeners(plugin, null);
    }

    /** Lists the listeners of one {@code plugin}, optionally only those of one {@code module}. */
    @NotNull
    default List<ListenerInfo> listeners(@NotNull PluginId plugin, @Nullable ModuleId module) {
        List<ListenerInfo> found = new ArrayList<ListenerInfo>();
        for (ListenerInfo info : listeners()) {
            if (info.getPlugin().equals(plugin) && (module == null || module.equals(info.getModule()))) {
                found.add(info);
            }
        }
        return found;
    }

    /** Resolves the class of a known event by its full class name, or {@code null} when it is not known. */
    @Nullable
    Class<? extends Event> eventType(@NotNull String name);

    /** Number of listeners that would run for {@code eventType}. */
    default int listenerCount(@NotNull Class<? extends Event> eventType) {
        return listeners(eventType).size();
    }

    /** Plugins that have at least one listener for {@code eventType}, in dispatch order without repeats. */
    @NotNull
    default List<PluginId> plugins(@NotNull Class<? extends Event> eventType) {
        List<PluginId> found = new ArrayList<PluginId>();
        for (ListenerInfo info : listeners(eventType)) {
            if (!found.contains(info.getPlugin())) {
                found.add(info.getPlugin());
            }
        }
        return found;
    }

    /**
     * Every event type that has a listener or has been called, with counters, ordered by name. Call counters are kept
     * for the whole process lifetime.
     */
    @NotNull
    List<EventInfo> events();

    /** The entry of one event type, or {@code null} when nobody listens to it and it was never called. */
    @Nullable
    default EventInfo event(@NotNull Class<? extends Event> eventType) {
        for (EventInfo info : events()) {
            if (info.getEventType().equals(eventType.getName())) {
                return info;
            }
        }
        return null;
    }

    /** Per-day call counts of every event, newest day first; see {@link #history(String)}. */
    @NotNull
    default List<EventDay> history() {
        return history(null);
    }

    /**
     * Per-day call counts, newest day first, for {@code eventType} or for every event when it is {@code null}. The
     * history is kept for {@link #getHistoryDays()} days, set by {@code eventHistoryDays} in the library settings, and
     * survives restarts.
     */
    @NotNull
    List<EventDay> history(@Nullable String eventType);

    /** How many days {@link #history} reaches back; {@code 0} means no history is kept. */
    int getHistoryDays();

    /** Per-hour counters of every event; see {@link #hourly(String)}. */
    @NotNull
    default List<EventHour> hourly() {
        return hourly(null);
    }

    /**
     * Per-hour counters of the last {@link #getHistoryDays()} days (at most 30), newest first and then by event name;
     * only {@code eventType} when given. Empty when the history is off.
     *
     * @param eventType class name of one event, or {@code null} for all
     * @return one entry per hour and event that was called
     */
    @NotNull
    List<EventHour> hourly(@Nullable String eventType);

    /** How many asynchronous events were accepted and still wait for a free thread. */
    int asyncQueued();

    /**
     * Writes the per-day and per-hour counters to a CSV file next to the history file.
     *
     * @return the file written, or {@code null} when the history is off or cannot be written
     */
    @Nullable
    Path exportHistoryCsv();

    /** The same as {@link #exportHistoryCsv()}, as a JSON file. */
    @Nullable
    Path exportHistoryJson();

    /** Starts recording the latest 32 dispatches of {@code eventType}; see {@link #enableTracing(Class, int)}. */
    default void enableTracing(@NotNull Class<? extends Event> eventType) {
        enableTracing(eventType, 32);
    }

    /**
     * Starts recording every dispatch of {@code eventType} and its subclasses, keeping the latest {@link EventTrace}s
     * up to {@code capacity}. Tracing costs nothing for event types that are not traced. Results are compared through
     * {@code toString()}, so give a custom result a readable one.
     */
    void enableTracing(@NotNull Class<? extends Event> eventType, int capacity);

    /** Stops recording {@code eventType}; recorded traces stay available until {@link #clearTraces()}. */
    void disableTracing(@NotNull Class<? extends Event> eventType);

    /** Event type names that are being traced now. */
    @NotNull
    List<String> tracedEvents();

    /** Recorded dispatches, oldest first. */
    @NotNull
    List<EventTrace> traces();

    /** Forgets every recorded trace. */
    void clearTraces();
}
