package org.pnfolder.pnlibrary.api.events

import java.time.LocalDate

/**
 * How often one event type was called on one day, returned by [EventInspector.history].
 *
 * @property date the day, in the library's display time zone
 * @property eventType class name of the event
 * @property calls how many times it was called that day
 * @property denied how many of those calls ended with [Event.isAllowed] equal to `false`
 */
data class EventDay(
    val date: LocalDate,
    val eventType: String,
    val calls: Long,
    val denied: Long,
)
