package org.pnfolder.pnlibrary.api.events.inspect;

import java.time.Instant;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Summary of one event type, returned by {@link EventInspector#events()}. */
public final class EventInfo {
    private final String eventType;
    private final int listeners;
    private final long calls;
    private final long denied;
    private final Instant lastCalledAt;

    /** Summary of an event type that has not been called. */
    public EventInfo(@NotNull String eventType, int listeners, long calls, long denied) {
        this(eventType, listeners, calls, denied, null);
    }

    /**
     * Summary of one event type.
     *
     * @param eventType class name of the event
     * @param listeners how many listeners would receive it now
     * @param calls how many times it has been called since the library started
     * @param denied how many of those calls ended with {@code Event.isAllowed()} equal to {@code false}
     * @param lastCalledAt when it was called last since the library started, or {@code null} when it has not been called
     */
    public EventInfo(@NotNull String eventType, int listeners, long calls, long denied, @Nullable Instant lastCalledAt) {
        this.eventType = Objects.requireNonNull(eventType, "eventType");
        this.listeners = listeners;
        this.calls = calls;
        this.denied = denied;
        this.lastCalledAt = lastCalledAt;
    }

    /** Class name of the event. */
    @NotNull
    public String getEventType() {
        return eventType;
    }

    /** How many listeners would receive it now. */
    public int getListeners() {
        return listeners;
    }

    /** How many times it has been called since the library started. */
    public long getCalls() {
        return calls;
    }

    /** How many of those calls ended denied. */
    public long getDenied() {
        return denied;
    }

    /** When it was called last since the library started, or {@code null} when it has not been called. */
    @Nullable
    public Instant getLastCalledAt() {
        return lastCalledAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof EventInfo)) {
            return false;
        }
        EventInfo that = (EventInfo) other;
        return listeners == that.listeners
                && calls == that.calls
                && denied == that.denied
                && eventType.equals(that.eventType)
                && Objects.equals(lastCalledAt, that.lastCalledAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventType, listeners, calls, denied, lastCalledAt);
    }

    @Override
    public String toString() {
        return "EventInfo(eventType=" + eventType + ", listeners=" + listeners + ", calls=" + calls + ", denied=" + denied
                + ", lastCalledAt=" + lastCalledAt + ")";
    }
}
