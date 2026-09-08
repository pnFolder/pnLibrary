@file:JvmName("PnEvents")

package ru.privatenull.pnlibrary.api.events

import java.util.function.Consumer

/** Kotlin shorthand for subscribing without passing an event class manually. */
inline fun <reified E : PnEvent> EventScope.subscribe(
    priority: EventPriority = EventPriority.NORMAL,
    ignoreCancelled: Boolean = false,
    noinline listener: (E) -> Unit,
): EventSubscription = subscribe(E::class.java, priority, ignoreCancelled, Consumer(listener))
