package ru.privatenull.pnlibrary.api.events

/** Marker interface for platform-independent events published through pnLibrary. */
interface PnEvent

/**
 * An event whose remaining cancellable listeners may be skipped.
 *
 * Cancellation does not stop dispatch by itself. A subscriber decides whether
 * to skip cancelled events through its `ignoreCancelled` option. Monitor
 * listeners always receive the final event state unless they opt out.
 */
interface CancellablePnEvent : PnEvent {
    var isCancelled: Boolean
}
