package ru.privatenull.pnlibrary.api.events

/**
 * Cross-platform listener order. Lower priorities run first.
 *
 * [MONITOR] is intended for observing the final result of an event. pnLibrary
 * does not enforce read-only access, so monitor listeners should not mutate it.
 */
enum class EventPriority {
    LOWEST,
    LOW,
    NORMAL,
    HIGH,
    HIGHEST,
    MONITOR,
}
