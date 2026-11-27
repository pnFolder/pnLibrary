package org.pnfolder.pnlibrary.kotlin

import org.pnfolder.pnlibrary.api.events.Event
import org.pnfolder.pnlibrary.api.events.bus.EventScope
import org.pnfolder.pnlibrary.api.events.listener.EventSubscription

/** Listens for events of type [E]: `scope.subscribe<PlayerBought> { event -> ... }`. */
public inline fun <reified E : Event> EventScope.subscribe(
    priority: Int = 0,
    noinline listener: (E) -> Unit,
): EventSubscription = subscribe(E::class.java, priority, listener)
