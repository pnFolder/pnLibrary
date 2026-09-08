package ru.privatenull.pnlibrary.api.events

/**
 * Process-wide platform-independent event bus.
 *
 * Use [scope] once per plugin owner and retain the returned handle. Event
 * classes and listeners depend only on `pnlibrary-api`, so the same code runs on
 * Bukkit, BungeeCord, and Velocity.
 */
interface EventService : AutoCloseable {
    /** Returns the existing owner scope or creates it atomically. */
    fun scope(owner: Any): EventScope

    /** Publishes [event] synchronously on the calling thread. */
    fun publish(event: Event): EventDispatchResult

    /** Removes and closes the scope belonging to [owner]. */
    fun close(owner: Any)

    /** Closes all scopes and prevents new subscriptions or publications. */
    override fun close()
}
