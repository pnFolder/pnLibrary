package org.pnfolder.pnlibrary.api.events.inspect.history;

import java.util.Objects;
import java.time.LocalDate;
import org.jetbrains.annotations.NotNull;

/** How often one event type was called on one day, returned by {@code EventInspector.history}. */
public final class EventDay {
    private final LocalDate date;
    private final String eventType;
    private final long calls;
    private final long denied;

    /**
     * Counters of one day.
     *
     * @param date the day, in the library's display time zone
     * @param eventType class name of the event
     * @param calls how many times it was called that day
     * @param denied how many of those calls ended with {@code Event.isAllowed()} equal to {@code false}
     */
    public EventDay(@NotNull LocalDate date, @NotNull String eventType, long calls, long denied) {
        this.date = Objects.requireNonNull(date, "date");
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.calls = calls;
        this.denied = denied;
    }

    /** The day, in the library's display time zone. */
    @NotNull
    public LocalDate getDate() {
        return date;
    }

    /** Class name of the event. */
    @NotNull
    public String getEventType() {
        return eventType;
    }

    /** How many times it was called that day. */
    public long getCalls() {
        return calls;
    }

    /** How many of those calls ended denied. */
    public long getDenied() {
        return denied;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EventDay)) {
            return false;
        }
        EventDay that = (EventDay) other;
        return calls == that.calls && denied == that.denied && date.equals(that.date) && eventType.equals(that.eventType);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(date, eventType, calls, denied);
    }

    @Override
    public String toString() {
        return "EventDay(date=" + date + ", eventType=" + eventType + ", calls=" + calls + ", denied=" + denied + ")";
    }
}
