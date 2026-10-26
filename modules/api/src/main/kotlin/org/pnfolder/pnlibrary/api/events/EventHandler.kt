package org.pnfolder.pnlibrary.api.events

/**
 * Marks a method as an event handler.
 *
 * The method must accept exactly one [Event] parameter and return `Unit` or
 * `void`. It may use any visibility. Smaller numeric priorities run first.
 *
 * @property priority Numeric dispatch order; any integer is valid.
 * @property ignoreDenied Skip the handler when the event is already denied ([Event.isAllowed] is `false`).
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class EventHandler(
    val priority: Int = EventPriority.NORMAL,
    val ignoreDenied: Boolean = false,
)
