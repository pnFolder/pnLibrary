package org.pnfolder.pnlibrary.api.events.inspect.history;

import java.time.LocalDateTime;
import org.jetbrains.annotations.NotNull;

/** How often one event type was called during one hour, returned by {@code EventInspector.hourly}. */
public final class EventHour {
    private final LocalDateTime hour;
    private final String eventType;
    private final long calls;
    private final long denied;

    /**
     * Counters of one hour.
     *
     * @param hour the start of the hour, in the library's display time zone
     * @param eventType class name of the event
     * @param calls how many times it was called that hour
     * @param denied how many of those calls ended with {@code Event.isAllowed()} equal to {@code false}
     */
    public EventHour(@NotNull LocalDateTime hour, @NotNull String eventType, long calls, long denied) {
        this.hour = hour;
        this.eventType = eventType;
        this.calls = calls;
        this.denied = denied;
    }

    /** The start of the hour, in the library's display time zone. */
    @NotNull
    public LocalDateTime getHour() {
        return hour;
    }

    /** Class name of the event. */
    @NotNull
    public String getEventType() {
        return eventType;
    }

    /** How many times it was called that hour. */
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
        if (!(other instanceof EventHour)) {
            return false;
        }
        EventHour that = (EventHour) other;
        return calls == that.calls && denied == that.denied && hour.equals(that.hour) && eventType.equals(that.eventType);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(hour, eventType, calls, denied);
    }

    @Override
    public String toString() {
        return "EventHour(hour=" + hour + ", eventType=" + eventType + ", calls=" + calls + ", denied=" + denied + ")";
    }
}
