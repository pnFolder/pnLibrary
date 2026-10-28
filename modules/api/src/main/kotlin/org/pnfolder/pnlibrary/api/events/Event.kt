package org.pnfolder.pnlibrary.api.events

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.runtime.PnLibraryProvider
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor

/**
 * Base class for every event published through the library event system.
 *
 * An event is dispatched in one of two [DispatchMode]s, chosen by how it is called:
 * [callEvent] runs listeners on the calling thread and the result is final when it returns;
 * [callEventAsync] runs them on a background thread (or a given executor) and delivers the result later.
 * While listeners run, [dispatchMode] and [dispatchThread] tell them which of the two they are in, so a listener
 * can check [isAsync] before it touches the game world.
 *
 * Details worth knowing:
 * - Before the first dispatch both are `null`; after a dispatch they keep the values of the latest one.
 * - Dispatching the same instance again overwrites them. Do not dispatch one instance from several threads at once.
 * - A listener that calls another event sees that event's own values; the outer event keeps its own.
 */
abstract class Event {
    /** User-friendly event identifier. Override when the class name is insufficient. */
    open val eventName: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        javaClass.simpleName.ifBlank { javaClass.name }
    }

    /**
     * Whether the event may proceed after listeners ran. A [ResultedEvent] reports its result;
     * any other event is always allowed.
     */
    val isAllowed: Boolean
        get() = (this as? ResultedEvent<*>)?.result?.isAllowed ?: true

    /** Denial reason from a [ResultedEvent.ComponentResult] set by a listener, or `null` when none was given. */
    val denyReason: Component?
        get() = ((this as? ResultedEvent<*>)?.result as? ResultedEvent.ComponentResult)?.reasonComponent

    @Volatile private var dispatch: Dispatch? = null

    private class Dispatch(val mode: DispatchMode, val thread: Thread)

    /** How the latest dispatch of this event was made, or `null` when it has not been dispatched yet. */
    val dispatchMode: DispatchMode? get() = dispatch?.mode

    /** The thread that ran the listeners of the latest dispatch, or `null` when it has not been dispatched yet. */
    val dispatchThread: Thread? get() = dispatch?.thread

    /** `true` when the latest dispatch was [DispatchMode.ASYNC]: the listeners must not touch the game world. */
    val isAsync: Boolean get() = dispatch?.mode == DispatchMode.ASYNC

    /** Called by the event bus right before the listeners run; closed to plugins by [InternalEventApi]. */
    @InternalEventApi
    fun bindDispatch(mode: DispatchMode, thread: Thread) {
        dispatch = Dispatch(mode, thread)
    }

    /**
     * Calls every matching listener before returning and returns [isAllowed] at that point.
     */
    fun callEvent(): Boolean {
        PnLibraryProvider.get().events.callEvent(this)
        return isAllowed
    }

    /**
     * Calls the listeners on the bus's background thread, or on [executor] when given, and completes with
     * [isAllowed] once all of them have run. Listeners then see [isAsync] as `true` (unless the executor hops
     * to the server thread, in which case they run there but are still reported as async).
     */
    @JvmOverloads
    fun callEventAsync(executor: Executor? = null): CompletionStage<Boolean> {
        val events = PnLibraryProvider.get().events
        val done = if (executor == null) events.callEventAsync(this) else events.callEventAsync(this, executor)
        return done.thenApply { isAllowed }
    }
}
