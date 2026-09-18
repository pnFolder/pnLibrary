package ru.privatenull.pnlibrary.api.events

/**
 * Detailed result produced when an [EventService.publish] future completes.
 *
 * The three counters describe mutually exclusive outcomes for matching listeners. A listener
 * skipped due to cancellation increments [skipped], while an invoked listener that throws
 * increments [failed] instead of [delivered].
 */
data class EventDispatchResult(
    /** Number of listeners invoked successfully. */
    val delivered: Int,
    /** Number of listeners skipped because the event was cancelled. */
    val skipped: Int,
    /** Number of listeners that threw an exception. */
    val failed: Int,
    /** Final cancellation state after every listener has run. */
    val cancelled: Boolean,
)
