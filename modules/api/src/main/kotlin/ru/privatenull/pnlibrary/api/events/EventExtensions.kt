@file:JvmName("EventDsl")

package ru.privatenull.pnlibrary.api.events

import ru.privatenull.pnlibrary.api.plugin.PluginId
import java.util.function.Consumer

/** Kotlin shorthand for subscribing without passing an event class manually. */
inline fun <reified E : Event> EventScope.subscribe(
    priority: Int = EventPriority.NORMAL,
    ignoreCancelled: Boolean = false,
    noinline listener: (E) -> Unit,
): EventSubscription = subscribe(E::class.java, priority, ignoreCancelled, Consumer(listener))

/** Kotlin shorthand for a plugin-ID-bound subscription through the global service. */
inline fun <reified E : Event> EventService.subscribe(
    pluginId: PluginId,
    priority: Int = EventPriority.NORMAL,
    ignoreCancelled: Boolean = false,
    noinline listener: (E) -> Unit,
): EventSubscription = subscribe(pluginId, E::class.java, priority, ignoreCancelled, Consumer(listener))
