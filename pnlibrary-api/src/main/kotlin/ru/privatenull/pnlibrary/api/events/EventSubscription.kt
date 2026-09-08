package ru.privatenull.pnlibrary.api.events

/** A single listener registration. Closing it is safe to repeat. */
interface EventSubscription : AutoCloseable {
    val isClosed: Boolean
    override fun close()
}
