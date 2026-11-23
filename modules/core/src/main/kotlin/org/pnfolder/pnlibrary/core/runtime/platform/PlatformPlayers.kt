package org.pnfolder.pnlibrary.core.runtime.platform

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.audience.ForwardingAudience
import org.pnfolder.pnlibrary.api.player.LibraryPlayer
import org.pnfolder.pnlibrary.api.player.PlayerService
import org.pnfolder.pnlibrary.api.sender.senderPointers
import org.pnfolder.pnlibrary.common.concurrent.FailureThrottle
import org.pnfolder.pnlibrary.common.lang.Failures
import org.pnfolder.pnlibrary.spi.player.PlatformPlayerAdapter
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Resolves consoles, players and composite audiences through the platform adapter; returns empty results once closed. */
internal class PlatformPlayers(
    private val adapter: PlatformPlayerAdapter,
    private val onDeliveryFailure: (Throwable) -> Unit = {},
) : PlayerService, AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val failureThrottle = FailureThrottle()

    override fun console(): Audience = if (closed.get()) CLOSED_CONSOLE else adapter.console()
    override fun player(uniqueId: UUID): LibraryPlayer? = if (closed.get()) null else adapter.player(uniqueId)
    override fun sender(native: Any): Audience? = if (closed.get()) null else adapter.sender(native)
    override fun onlinePlayers(): List<LibraryPlayer> = if (closed.get()) emptyList() else
        Collections.unmodifiableList(ArrayList(adapter.onlinePlayers()))
    override fun all(): Audience = Group { onlinePlayers() }
    override fun combine(audiences: Iterable<Audience>): Audience = Group { audiences }
    override fun close() { closed.set(true) }

    /** Sends to every receiver; one that fails does not stop the others, and the failure is reported once in a while. */
    private inner class Group(private val receivers: () -> Iterable<Audience>) : ForwardingAudience {
        override fun audiences(): Iterable<Audience> = receivers().distinctByIdentity().map { Guarded(it) }

        private inner class Guarded(private val receiver: Audience) : ForwardingAudience.Single {
            override fun audience(): Audience = receiver

            override fun sendMessage(message: net.kyori.adventure.text.Component) = guard { receiver.sendMessage(message) }

            override fun sendActionBar(message: net.kyori.adventure.text.Component) = guard { receiver.sendActionBar(message) }

            override fun playSound(sound: net.kyori.adventure.sound.Sound) = guard { receiver.playSound(sound) }

            override fun showTitle(title: net.kyori.adventure.title.Title) = guard { receiver.showTitle(title) }

            private inline fun guard(operation: () -> Unit) {
                try {
                    operation()
                } catch (error: Throwable) {
                    Failures.rethrowIfFatal(error)
                    if (failureThrottle.shouldReport(error.javaClass.name)) onDeliveryFailure(error)
                }
            }
        }
    }

    private companion object {
        val CLOSED_CONSOLE = object : Audience {
            override fun pointers() = senderPointers("Console") { false }
        }

        fun Iterable<Audience>.distinctByIdentity(): List<Audience> {
            val seen = Collections.newSetFromMap(IdentityHashMap<Audience, Boolean>())
            return filter(seen::add)
        }
    }
}
