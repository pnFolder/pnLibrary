package ru.privatenull.pnlibrary.api.events

/**
 * Suggested numeric listener priorities. Lower numbers run first.
 *
 * These constants are conveniences, not a closed list. Any integer may be
 * passed to `EventScope.subscribe`, for example `priority = 250` between
 * [NORMAL] and [HIGH]. Equal priorities retain registration order.
 *
 * [MONITOR] is intended for observing the final result. pnLibrary does not
 * enforce read-only access, so monitor listeners should not mutate the event.
 */
object EventPriority {
    const val LOWEST: Int = -1_000
    const val LOW: Int = -500
    const val NORMAL: Int = 0
    const val HIGH: Int = 500
    const val HIGHEST: Int = 1_000
    const val MONITOR: Int = 2_000
}
