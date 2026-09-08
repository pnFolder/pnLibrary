package ru.privatenull.pnlibrary.api.events

/** Marker interface for events published through the library event system. */
interface Event

/**
 * An event whose remaining cancellable handlers may be skipped.
 *
 * Cancellation does not stop dispatch by itself. A handler decides whether to
 * skip cancelled events through its `ignoreCancelled` option.
 */
interface CancellableEvent : Event {
    var isCancelled: Boolean
}
