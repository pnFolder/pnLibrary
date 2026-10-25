package org.pnfolder.pnlibrary.core.audiences

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.audiences.AudienceSender
import org.pnfolder.pnlibrary.api.audiences.AudienceService
import org.pnfolder.pnlibrary.api.audiences.Delivery
import org.pnfolder.pnlibrary.api.audiences.LibraryAudience
import org.pnfolder.pnlibrary.api.audiences.LibraryPlayer
import org.pnfolder.pnlibrary.common.lang.Failures
import org.pnfolder.pnlibrary.spi.audiences.PlatformAudienceAdapter
import java.time.Duration
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Resolves consoles, players and composite audiences through the platform adapter; returns empty results once closed. */
internal class AudienceServiceImpl(
    private val adapter: PlatformAudienceAdapter,
) : AudienceService, AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun console(): AudienceSender = if (closed.get()) CLOSED_CONSOLE else adapter.console()
    override fun player(uniqueId: UUID): LibraryPlayer? = if (closed.get()) null else adapter.player(uniqueId)
    override fun sender(native: Any): AudienceSender? = if (closed.get()) null else adapter.sender(native)
    override fun onlinePlayers(): List<LibraryPlayer> = if (closed.get()) emptyList() else
        Collections.unmodifiableList(ArrayList(adapter.onlinePlayers()))
    override fun all(): LibraryAudience = DynamicAudience(::onlinePlayers)
    override fun combine(audiences: Iterable<LibraryAudience>): LibraryAudience = CompositeAudience(audiences)
    override fun close() { closed.set(true) }

    private class DynamicAudience(private val receivers: () -> Iterable<LibraryAudience>) : LibraryAudience {
        override fun sendMessage(text: Component) = CompositeAudience(receivers()).sendMessage(text)
        override fun actionBar(text: Component) = CompositeAudience(receivers()).actionBar(text)
        override fun playSound(sound: Sound) = CompositeAudience(receivers()).playSound(sound)
        override fun showTitle(title: Component, subtitle: Component, fadeIn: Duration, stay: Duration, fadeOut: Duration) =
            CompositeAudience(receivers()).showTitle(title, subtitle, fadeIn, stay, fadeOut)
    }

    private class CompositeAudience(receivers: Iterable<LibraryAudience>) : LibraryAudience {
        private val receivers = receivers.filterDistinctByIdentity()
        override fun sendMessage(text: Component) = each { it.sendMessage(text) }
        override fun actionBar(text: Component) = each { it.actionBar(text) }
        override fun playSound(sound: Sound): Delivery = deliverEach { it.playSound(sound) }

        override fun showTitle(title: Component, subtitle: Component, fadeIn: Duration, stay: Duration, fadeOut: Duration) =
            deliverEach { it.showTitle(title, subtitle, fadeIn, stay, fadeOut) }

        private inline fun deliverEach(operation: (LibraryAudience) -> Delivery): Delivery =
            Delivery.combine(receivers.map { receiver ->
                try {
                    operation(receiver)
                } catch (error: Throwable) {
                    Failures.rethrowIfFatal(error)
                    Delivery.Failed(error)
                }
            })

        private inline fun each(operation: (LibraryAudience) -> Unit) {
            receivers.forEach { receiver ->
                try {
                    operation(receiver)
                } catch (error: Throwable) {
                    Failures.rethrowIfFatal(error)
                }
            }
        }
    }

    private companion object {
        val CLOSED_CONSOLE = object : AudienceSender {
            override val id = "console"
            override val name = "Console"
            override val isConsole = true
            override fun hasPermission(permission: String) = false
            override fun sendMessage(text: Component) = Unit
            override fun actionBar(text: Component) = Unit
            override fun playSound(sound: Sound) = Delivery.Unsupported
        }

        fun Iterable<LibraryAudience>.filterDistinctByIdentity(): List<LibraryAudience> {
            val seen = Collections.newSetFromMap(IdentityHashMap<LibraryAudience, Boolean>())
            return filter(seen::add)
        }
    }
}
