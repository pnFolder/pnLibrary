package org.pnfolder.pnlibrary.api.events.listener;

/**
 * Suggested numeric listener priorities. Lower numbers run first.
 *
 * <p>These constants are conveniences, not a closed list. Any integer may be passed to
 * {@code EventScope.subscribe}, for example {@code 250} between {@link #NORMAL} and {@link #HIGH}. Equal priorities
 * retain registration order.
 *
 * <p>{@link #MONITOR} is intended for observing the final result. pnLibrary does not enforce read-only access, so
 * monitor listeners should not mutate the event.
 */
public final class EventPriority {
    /** Earliest conventional phase, suitable for initial validation or defaults. */
    public static final int LOWEST = -1_000;
    /** Early conventional phase. */
    public static final int LOW = -500;
    /** Default phase used when no explicit priority is supplied. */
    public static final int NORMAL = 0;
    /** Late conventional phase. */
    public static final int HIGH = 500;
    /** Latest conventional phase in which normal mutation should occur. */
    public static final int HIGHEST = 1_000;
    /** Observation phase intended for inspecting the final event state. */
    public static final int MONITOR = 2_000;

    private EventPriority() {}
}
