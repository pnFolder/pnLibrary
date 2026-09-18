package ru.privatenull.pnlibrary.api.events

/**
 * Handle for one typed listener registration.
 *
 * Closing the handle removes only this listener and is safe to repeat.
 */
interface EventSubscription : AutoCloseable {
    /** Whether this listener has been removed. */
    val isClosed: Boolean

    /** Removes this listener from future dispatches. */
    override fun close()
}
