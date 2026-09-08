package ru.privatenull.pnlibrary.api.events

import ru.privatenull.pnlibrary.api.runtime.PnLibraryProvider
import java.util.concurrent.CompletableFuture

/**
 * Base class for every event published through the library event system.
 *
 * [mode] selects where listeners execute. Synchronous events use the
 * platform's main/global scheduler; asynchronous events use the library's
 * background executor.
 */
abstract class Event @JvmOverloads constructor(
    val mode: EventMode = EventMode.SYNC,
) {
    /** Compatibility-friendly view of [mode]. */
    val isAsynchronous: Boolean get() = mode == EventMode.ASYNC

    /** User-friendly event identifier. Override when the class name is insufficient. */
    open val eventName: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        javaClass.simpleName.ifBlank { javaClass.name }
    }

    /**
     * Schedules this event in its declared [mode].
     *
     * The returned future completes with `false` only when this event implements
     * [Cancellable] and ends cancelled. Ignoring the future is valid for
     * fire-and-forget events; callers that need the final state should compose or
     * await it without blocking a platform-owned thread.
     */
    fun callEvent(): CompletableFuture<Boolean> =
        PnLibraryProvider.get().events.publish(this)
            .thenApply { result -> !result.cancelled }
}
