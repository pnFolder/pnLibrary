package ru.privatenull.pnlibrary.api.events

import ru.privatenull.pnlibrary.api.runtime.PnLibraryProvider

/**
 * Base class for every event published through the library event system.
 *
 * Dispatch is synchronous and always uses the calling thread. Use the task service explicitly
 * when an event must be fired from a background context.
 */
abstract class Event {
    /** User-friendly event identifier. Override when the class name is insufficient. */
    open val eventName: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        javaClass.simpleName.ifBlank { javaClass.name }
    }

    /**
     * Calls every matching listener before returning. Returns `false` when this event implements
     * [Cancellable] and a listener cancelled it; otherwise returns `true`.
     */
    fun callEvent(): Boolean {
        PnLibraryProvider.get().events.callEvent(this)
        return (this as? Cancellable)?.isCancelled != true
    }
}
