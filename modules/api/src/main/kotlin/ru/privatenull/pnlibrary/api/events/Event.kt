package ru.privatenull.pnlibrary.api.events

import net.kyori.adventure.text.Component
import ru.privatenull.pnlibrary.api.runtime.PnLibraryProvider

/**
 * Base class for every event published through the library event system.
 *
 * Dispatch is synchronous and always uses the calling thread. Use the task service explicitly
 * when an event must be fired from a background context.
 */
abstract class Event {
    /** User-friendly event identifier. Override when the class name is insufficient. */
    open val eventName: String by lazy(LazyThreadSafetyMode.PUBLICATION) {
        javaClass.simpleName.ifBlank { javaClass.name }
    }

    /**
     * Whether the event may proceed after listeners ran. A [ResultedEvent] reports its result;
     * any other event is always allowed.
     */
    val isAllowed: Boolean
        get() = (this as? ResultedEvent<*>)?.result?.isAllowed ?: true

    /** Denial reason from a [ResultedEvent.ComponentResult] set by a listener, or `null` when none was given. */
    val denyReason: Component?
        get() = ((this as? ResultedEvent<*>)?.result as? ResultedEvent.ComponentResult)?.reasonComponent

    /**
     * Calls every matching listener before returning and returns [isAllowed] at that point.
     */
    fun callEvent(): Boolean {
        PnLibraryProvider.get().events.callEvent(this)
        return isAllowed
    }
}
