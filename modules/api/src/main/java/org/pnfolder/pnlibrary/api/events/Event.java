package org.pnfolder.pnlibrary.api.events;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import net.kyori.adventure.text.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.events.bus.DispatchMode;
import org.pnfolder.pnlibrary.api.events.bus.EventService;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryProvider;

/**
 * Base class for every event published through the library event system.
 *
 * <p>An event is dispatched in one of two {@link DispatchMode}s, chosen by how it is called: {@link #callEvent()} runs
 * listeners on the calling thread and the result is final when it returns; {@link #callEventAsync()} runs them on a
 * background thread (or a given executor) and delivers the result later. While listeners run,
 * {@link #getDispatchMode()} and {@link #getDispatchThread()} tell them which of the two they are in, so a listener
 * can check {@link #isAsync()} before it touches the game world.
 *
 * <p>Details worth knowing:
 * <ul>
 *   <li>Before the first dispatch both are {@code null}; after a dispatch they keep the values of the latest one.
 *   <li>Dispatching the same instance again overwrites them. Do not dispatch one instance from several threads at once.
 *   <li>A listener that calls another event sees that event's own values; the outer event keeps its own.
 * </ul>
 */
public abstract class Event {
    private volatile String eventName;
    private volatile Dispatch dispatch;

    /** User-friendly event identifier. Override when the class name is insufficient. */
    @NotNull
    public String getEventName() {
        String name = eventName;
        if (name == null) {
            String simple = getClass().getSimpleName();
            name = simple.trim().isEmpty() ? getClass().getName() : simple;
            eventName = name;
        }
        return name;
    }

    /**
     * Whether the event may proceed after listeners ran. A {@link ResultedEvent} reports its result; any other event is
     * always allowed.
     */
    public final boolean isAllowed() {
        if (!(this instanceof ResultedEvent)) {
            return true;
        }
        ResultedEvent.Result result = ((ResultedEvent<?>) this).getResult();
        return result == null || result.isAllowed();
    }

    /** Denial reason from a {@link ResultedEvent.ComponentResult} set by a listener, or {@code null} when none was given. */
    @Nullable
    public final Component getDenyReason() {
        if (!(this instanceof ResultedEvent)) {
            return null;
        }
        Object result = ((ResultedEvent<?>) this).getResult();
        return result instanceof ResultedEvent.ComponentResult ? ((ResultedEvent.ComponentResult) result).getReason() : null;
    }

    /** How the latest dispatch of this event was made, or {@code null} when it has not been dispatched yet. */
    @Nullable
    public final DispatchMode getDispatchMode() {
        Dispatch latest = dispatch;
        return latest == null ? null : latest.mode;
    }

    /** The thread that ran the listeners of the latest dispatch, or {@code null} when it has not been dispatched yet. */
    @Nullable
    public final Thread getDispatchThread() {
        Dispatch latest = dispatch;
        return latest == null ? null : latest.thread;
    }

    /** {@code true} when the latest dispatch was {@link DispatchMode#ASYNC}: the listeners must not touch the game world. */
    public final boolean isAsync() {
        Dispatch latest = dispatch;
        return latest != null && latest.mode == DispatchMode.ASYNC;
    }

    /** Called by the event bus right before the listeners run; plugins must not call it. */
    @InternalEventApi
    public final void bindDispatch(@NotNull DispatchMode mode, @NotNull Thread thread) {
        dispatch = new Dispatch(mode, thread);
    }

    /** Calls every matching listener before returning and returns {@link #isAllowed()} at that point. */
    public final boolean callEvent() {
        PnLibraryProvider.get().getEvents().callEvent(this);
        return isAllowed();
    }

    /**
     * Calls the listeners on the bus's background thread and completes with {@link #isAllowed()} once all of them have
     * run. Listeners then see {@link #isAsync()} as {@code true}.
     */
    @NotNull
    public final CompletionStage<Boolean> callEventAsync() {
        return callEventAsync(null);
    }

    /**
     * Calls the listeners on {@code executor}, or on the bus's background thread when it is {@code null}, and
     * completes with {@link #isAllowed()} once all of them have run. Listeners then see {@link #isAsync()} as
     * {@code true} (unless the executor hops to the server thread, in which case they run there but are still
     * reported as async).
     */
    @NotNull
    public final CompletionStage<Boolean> callEventAsync(@Nullable Executor executor) {
        EventService events = PnLibraryProvider.get().getEvents();
        CompletionStage<Event> done = executor == null ? events.callEventAsync(this) : events.callEventAsync(this, executor);
        return done.thenApply(event -> isAllowed());
    }

    private static final class Dispatch {
        final DispatchMode mode;
        final Thread thread;

        Dispatch(DispatchMode mode, Thread thread) {
            this.mode = mode;
            this.thread = thread;
        }
    }
}
