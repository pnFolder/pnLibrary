package org.pnfolder.pnlibrary.api.events

import java.time.Instant

/**
 * Summary of one event type, returned by [EventInspector.events].
 *
 * @property eventType class name of the event
 * @property listeners how many listeners would receive it now
 * @property calls how many times it has been called since the library started
 * @property denied how many of those calls ended with [Event.isAllowed] equal to `false`
 * @property lastCalledAt when it was called last since the library started, or `null` when it has not been called
 */
data class EventInfo(
    val eventType: String,
    val listeners: Int,
    val calls: Long,
    val denied: Long,
    val lastCalledAt: Instant? = null,
)
