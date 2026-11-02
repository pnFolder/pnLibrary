package org.pnfolder.pnlibrary.core.events

import org.pnfolder.pnlibrary.api.events.EventListenerRegistration
import org.pnfolder.pnlibrary.api.events.EventSubscription
import org.pnfolder.pnlibrary.api.events.Listener
import java.util.concurrent.atomic.AtomicBoolean

/** All the subscriptions made for the handlers of one listener object; closing it closes every one. */
internal class ListenerRegistration(
    override val listener: Listener,
    private val subscriptions: List<EventSubscription>,
) : EventListenerRegistration {
    private val registrationClosed = AtomicBoolean(false)
    override val handlerCount: Int get() = subscriptions.size
    override val isClosed: Boolean
        get() = registrationClosed.get() || subscriptions.all { it.isClosed }

    override fun close() {
        if (!registrationClosed.compareAndSet(false, true)) return
        subscriptions.forEach { it.close() }
    }
}
