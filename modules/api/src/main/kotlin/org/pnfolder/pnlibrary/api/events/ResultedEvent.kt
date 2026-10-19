package org.pnfolder.pnlibrary.api.events

import net.kyori.adventure.text.Component
import java.util.Optional

/**
 * Event whose listeners set a typed [Result] that the caller reads after dispatch.
 *
 * Each event overrides [result] and sets its starting value, which is used until a listener
 * replaces it. For example:
 * `class PurchaseEvent : ResultedEvent<ResultedEvent.ComponentResult>() {
 *     override var result: ResultedEvent.ComponentResult = ResultedEvent.ComponentResult.allowed()
 * }`
 *
 * @param R result type carried by this event
 */
abstract class ResultedEvent<R : ResultedEvent.Result> : Event() {
    /**
     * Current outcome. Listeners may replace it; the final value is available as soon as
     * [Event.callEvent] returns.
     */
    abstract var result: R

    /** Outcome that listeners leave on a [ResultedEvent]. Implement it to carry extra data. */
    interface Result {
        /** Whether the event may proceed after dispatch. */
        val isAllowed: Boolean
    }

    /** Plain allow/deny outcome without a reason. */
    class GenericResult private constructor(
        override val isAllowed: Boolean,
    ) : Result {
        /** Renders as `allowed` or `denied`. */
        override fun toString(): String = if (isAllowed) "allowed" else "denied"

        /** Factory methods for [GenericResult]. */
        companion object {
            private val ALLOWED = GenericResult(true)
            private val DENIED = GenericResult(false)

            /** Returns the shared allowed result. */
            @JvmStatic
            fun allowed(): GenericResult = ALLOWED

            /** Returns the shared denied result. */
            @JvmStatic
            fun denied(): GenericResult = DENIED
        }
    }

    /**
     * Allow/deny outcome with an optional denial reason rendered as an Adventure [Component].
     *
     * Use [allowed] or [denied]. [denied] takes a [Component], so messages can be styled and
     * localized the same way as the rest of the library.
     */
    class ComponentResult private constructor(
        override val isAllowed: Boolean,
        private val reason: Component?,
    ) : Result {
        /** Denial reason, or `null` when none was given or the result is allowed. */
        val reasonComponent: Component? get() = reason

        /** Java-friendly view of [reasonComponent]. */
        fun getReasonComponent(): Optional<Component> = Optional.ofNullable(reason)

        /** Renders as `allowed` or `denied`. */
        override fun toString(): String = if (isAllowed) "allowed" else "denied"

        /** Factory methods for [ComponentResult]. */
        companion object {
            private val ALLOWED = ComponentResult(true, null)

            /** Returns the shared allowed result. */
            @JvmStatic
            fun allowed(): ComponentResult = ALLOWED

            /** Returns a denied result that explains why. */
            @JvmStatic
            fun denied(reason: Component): ComponentResult = ComponentResult(false, reason)
        }
    }
}
