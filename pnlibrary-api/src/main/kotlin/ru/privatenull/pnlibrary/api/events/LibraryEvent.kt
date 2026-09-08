package ru.privatenull.pnlibrary.api.events

/** Marker interface for platform-independent events published through pnLibrary. */
interface LibraryEvent

/**
 * An event whose remaining cancellable handlers may be skipped.
 *
 * Cancellation does not stop dispatch by itself. A subscriber decides whether
 * to skip cancelled events through its `ignoreCancelled` option. Monitor
 * handlers always receive the final event state unless they opt out.
 */
interface CancellableEvent : LibraryEvent {
    var isCancelled: Boolean
}
