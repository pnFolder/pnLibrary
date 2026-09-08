package ru.privatenull.pnlibrary.api.events

/**
 * Marks a method as a library event handler.
 *
 * The method must accept exactly one [LibraryEvent] parameter and return `Unit`
 * or `void`. It may use any visibility. Smaller numeric priorities run first.
 *
 * @property priority Numeric dispatch order; any integer is valid.
 * @property ignoreCancelled Skip the method after a cancellable event is cancelled.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class HandlesEvent(
    val priority: Int = EventPriority.NORMAL,
    val ignoreCancelled: Boolean = false,
)
