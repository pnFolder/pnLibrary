package org.pnfolder.pnlibrary.api.events

import java.time.LocalDateTime

/**
 * How often one event type was called during one hour, returned by [EventInspector.hourly].
 *
 * @property hour the start of the hour, in the library's display time zone
 * @property eventType class name of the event
 * @property calls how many times it was called in that hour
 * @property denied how many of those calls ended with [Event.isAllowed] equal to `false`
 */
data class EventHour(
    val hour: LocalDateTime,
    val eventType: String,
    val calls: Long,
    val denied: Long,
)
