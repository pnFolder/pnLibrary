package ru.privatenull.pnlibrary.api.events

/**
 * Optional capability implemented by an [Event] that supports cancellation.
 *
 * Cancellation does not stop dispatch by itself. A handler decides whether to
 * skip cancelled events through its `ignoreCancelled` option.
 */
interface Cancellable {
    var isCancelled: Boolean
}
