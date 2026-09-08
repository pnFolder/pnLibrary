package ru.privatenull.pnlibrary.api.events

import ru.privatenull.pnlibrary.api.runtime.PnLibraryProvider
import java.util.concurrent.CompletionStage

/**
 * Base class for every event published through the library event system.
 *
 * A synchronous event is dispatched inline on the calling thread. An asynchronous
 * event must use [callAsync] or [EventService.publishAsync] and runs on the event
 * executor. Async listeners must not call thread-confined native platform APIs.
 */
abstract class Event @JvmOverloads constructor(
    val isAsynchronous: Boolean = false,
) {
    /** User-friendly event identifier. Override when the class name is insufficient. */
    open val eventName: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        javaClass.simpleName.ifBlank { javaClass.name }
    }

    /** Dispatches this synchronous event through the installed pnLibrary runtime. */
    fun call(): EventDispatchResult = PnLibraryProvider.get().events.publish(this)

    /** Dispatches synchronously and returns `false` when this event ends cancelled. */
    fun callEvent(): Boolean = !call().cancelled

    /** Dispatches this asynchronous event on the event executor. */
    fun callAsync(): CompletionStage<EventDispatchResult> =
        PnLibraryProvider.get().events.publishAsync(this)

    /** Dispatches asynchronously and resolves to `false` when the event ends cancelled. */
    fun callEventAsync(): CompletionStage<Boolean> = callAsync().thenApply { !it.cancelled }
}
