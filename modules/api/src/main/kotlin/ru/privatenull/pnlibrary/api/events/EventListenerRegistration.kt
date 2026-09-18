package ru.privatenull.pnlibrary.api.events

/** Registration containing every annotated handler discovered on one listener. */
interface EventListenerRegistration : AutoCloseable {
    /** Registered listener instance. */
    val listener: Listener

    /** Number of handler methods registered from the listener. */
    val handlerCount: Int

    /** Whether all handler subscriptions have been removed. */
    val isClosed: Boolean

    /** Removes every handler belonging to this registration. Safe to repeat. */
    override fun close()
}
