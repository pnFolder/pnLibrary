package org.pnfolder.pnlibrary.spi.commands

import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import java.util.LinkedHashSet
import java.util.concurrent.atomic.AtomicBoolean

/** Shared ownership and shutdown lifecycle for native command registrations. */
abstract class TrackingPlatformCommandAdapter : PlatformCommandAdapter {
    private val closed = AtomicBoolean(false)
    private val registrations = LinkedHashSet<TrackedRegistration>()

    protected abstract fun registerNative(
        owner: Any,
        command: CommandDefinition,
        dispatcher: PlatformCommandDispatcher,
    ): PlatformCommandRegistration

    final override fun register(
        owner: Any,
        command: CommandDefinition,
        dispatcher: PlatformCommandDispatcher,
    ): PlatformCommandRegistration {
        check(!closed.get()) { "Platform command adapter is closed" }
        return TrackedRegistration(registerNative(owner, command, dispatcher)).also { registration ->
            synchronized(registrations) { registrations += registration }
            if (closed.get()) registration.close()
        }
    }

    final override fun close() {
        if (!closed.compareAndSet(false, true)) return
        val pending = synchronized(registrations) { registrations.toList().asReversed() }
        var failure: Throwable? = null
        pending.forEach { registration ->
            try {
                registration.close()
            } catch (error: Throwable) {
                if (failure == null) failure = error
            }
        }
        synchronized(registrations) { registrations.clear() }
        failure?.let { throw it }
    }

    private inner class TrackedRegistration(
        private val delegate: PlatformCommandRegistration,
    ) : PlatformCommandRegistration {
        private val registrationClosed = AtomicBoolean(false)

        override fun close() {
            if (!registrationClosed.compareAndSet(false, true)) return
            synchronized(registrations) { registrations.remove(this) }
            delegate.close()
        }
    }
}
