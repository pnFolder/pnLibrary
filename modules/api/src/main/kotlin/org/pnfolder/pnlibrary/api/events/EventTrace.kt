package org.pnfolder.pnlibrary.api.events

import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginId

/**
 * One finished dispatch of an event, recorded while tracing is enabled for its type.
 *
 * ```kotlin
 * events.inspector.enableTracing(PurchaseEvent::class.java)
 * PurchaseEvent(player, 1500).callEvent()
 * events.inspector.traces().forEach { trace -> trace.steps.forEach(::println) }
 * ```
 *
 * @property eventName [Event.eventName] of the dispatched event
 * @property allowed [Event.isAllowed] once every listener had run
 * @property result `toString()` of the final [ResultedEvent.result], or `null` for events without a result
 * @property steps one entry per matching listener, in the order they were considered
 * @property mode how the event was dispatched
 * @property thread name of the thread the listeners ran on
 */
data class EventTrace(
    val eventName: String,
    val allowed: Boolean,
    val result: String?,
    val steps: List<Step>,
    val mode: DispatchMode = DispatchMode.SYNC,
    val thread: String = "",
) {
    /**
     * What happened to one listener during a single dispatch.
     *
     * @property plugin plugin that registered the listener
     * @property module module of that plugin, or `null` for a listener registered directly on the bus
     * @property priority priority the listener was registered with
     * @property skipped `true` when the listener did not run because the event was already denied and the
     * listener asked to ignore denied events
     * @property failed `true` when the listener threw; the error is logged and dispatch continues
     * @property nanos time spent inside the listener, `0` when it was skipped
     * @property resultBefore `toString()` of the result before the listener ran, or `null` without a result
     * @property resultAfter `toString()` of the result after the listener ran, or `null` without a result
     */
    data class Step(
        val plugin: PluginId,
        val module: ModuleId?,
        val priority: Int,
        val skipped: Boolean,
        val failed: Boolean,
        val nanos: Long,
        val resultBefore: String?,
        val resultAfter: String?,
    ) {
        /** Whether the listener replaced the result, judged by comparing [resultBefore] and [resultAfter]. */
        val changedResult: Boolean get() = resultBefore != resultAfter
    }
}
