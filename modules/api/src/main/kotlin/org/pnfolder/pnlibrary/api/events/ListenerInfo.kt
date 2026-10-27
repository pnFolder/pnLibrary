package org.pnfolder.pnlibrary.api.events

import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginId

/**
 * Read-only description of one registered listener, returned by [EventService.listeners].
 *
 * ```kotlin
 * events.inspector.listeners(PurchaseEvent::class.java).forEach { println("${it.plugin}/${it.module} ${it.priority} ${it.source}") }
 * ```
 *
 * @property plugin plugin that registered the listener
 * @property module module of that plugin that registered it, or `null` when the listener was registered
 * directly on the bus rather than through a module
 * @property eventType name of the class the listener subscribed to; it also receives subclasses
 * @property priority priority the listener was registered with; smaller values run first
 * @property ignoreDenied whether the listener is skipped once the event is denied
 * @property source `Class#method` for an `@EventHandler` method, or the listener class for a lambda
 * @property invocations how many times the listener has run since it was registered
 * @property failures how many of those runs threw an exception
 */
data class ListenerInfo(
    val plugin: PluginId,
    val module: ModuleId?,
    val eventType: String,
    val priority: Int,
    val ignoreDenied: Boolean,
    val source: String,
    val invocations: Long,
    val failures: Long,
)
