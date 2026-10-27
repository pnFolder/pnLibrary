package org.pnfolder.pnlibrary.api.events

/**
 * How an event was dispatched, shown by [EventTrace] and `/pn bus`.
 *
 * Listeners of a [SYNC] dispatch run on the calling thread, so they may touch the game world when the caller is on
 * the server thread. Listeners of an [ASYNC] dispatch run on a background thread and must not touch the world.
 */
enum class DispatchMode {
    /** [EventService.callEvent]: on the calling thread, the result is final when the call returns. */
    SYNC,

    /** [EventService.callEventAsync]: on the bus thread or a given executor, the result arrives through a [java.util.concurrent.CompletionStage]. */
    ASYNC,
}
