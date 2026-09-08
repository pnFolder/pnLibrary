package ru.privatenull.pnlibrary.api.events

import ru.privatenull.pnlibrary.api.runtime.PnLibraryProvider

/**
 * Base class for every event published through the library event system.
 *
 * Like Bukkit, [isAsynchronous] describes how callers are allowed to use the
 * event; it does not select or create a thread. Dispatch always runs inline on
 * the calling thread. Code firing an asynchronous event is responsible for
 * already running in a suitable asynchronous context.
 */
abstract class Event @JvmOverloads constructor(
    val isAsynchronous: Boolean = false,
) {
    /** User-friendly event identifier. Override when the class name is insufficient. */
    open val eventName: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        javaClass.simpleName.ifBlank { javaClass.name }
    }

    /**
     * Dispatches this event through the installed runtime and tests cancellation.
     * Returns `false` only when this event implements [Cancellable] and ends cancelled.
     */
    fun callEvent(): Boolean {
        val result = PnLibraryProvider.get().events.publish(this)
        return !result.cancelled
    }
}
