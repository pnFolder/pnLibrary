@file:JvmName("EventDsl")

package org.pnfolder.pnlibrary.api.events.listener

import java.util.function.Consumer
import org.pnfolder.pnlibrary.api.events.Event
import org.pnfolder.pnlibrary.api.events.bus.EventScope
import org.pnfolder.pnlibrary.api.events.bus.EventService
import org.pnfolder.pnlibrary.api.plugin.PluginId

/** Kotlin shorthand for subscribing without passing an event class manually. */
inline fun <reified E : Event> EventScope.subscribe(
    priority: Int = EventPriority.NORMAL,
    ignoreDenied: Boolean = false,
    noinline listener: (E) -> Unit,
): EventSubscription = subscribe(E::class.java, priority, ignoreDenied, Consumer(listener))

/** Kotlin shorthand for a plugin-ID-bound subscription through the global service. */
inline fun <reified E : Event> EventService.subscribe(
    pluginId: PluginId,
    priority: Int = EventPriority.NORMAL,
    ignoreDenied: Boolean = false,
    noinline listener: (E) -> Unit,
): EventSubscription = subscribe(pluginId, E::class.java, priority, ignoreDenied, Consumer(listener))
