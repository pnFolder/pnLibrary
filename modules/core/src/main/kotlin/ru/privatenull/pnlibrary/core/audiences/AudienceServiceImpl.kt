package ru.privatenull.pnlibrary.core.audiences

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import ru.privatenull.pnlibrary.api.audiences.AudienceSender
import ru.privatenull.pnlibrary.api.audiences.AudienceService
import ru.privatenull.pnlibrary.api.actions.LibraryAudience
import ru.privatenull.pnlibrary.api.actions.LibraryPlayer
import ru.privatenull.pnlibrary.spi.audiences.PlatformAudienceAdapter
import java.util.Collections
import java.util.IdentityHashMap
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

internal class AudienceServiceImpl(
    private val adapter: PlatformAudienceAdapter,
) : AudienceService, AutoCloseable {
    private val closed = AtomicBoolean(false)

    override fun console(): AudienceSender = if (closed.get()) CLOSED_CONSOLE else adapter.console()
    override fun player(uniqueId: UUID): LibraryPlayer? = if (closed.get()) null else adapter.player(uniqueId)
    override fun sender(native: Any): AudienceSender? = if (closed.get()) null else adapter.sender(native)
    override fun onlinePlayers(): List<LibraryPlayer> = if (closed.get()) emptyList() else adapter.onlinePlayers().toList()
    override fun all(): LibraryAudience = DynamicAudience(::onlinePlayers)
    override fun combine(audiences: Iterable<LibraryAudience>): LibraryAudience = CompositeAudience(audiences)
    override fun close() { closed.set(true) }

    private class DynamicAudience(private val receivers: () -> Iterable<LibraryAudience>) : LibraryAudience {
        override fun sendMessage(text: Component) = CompositeAudience(receivers()).sendMessage(text)
        override fun actionBar(text: Component) = CompositeAudience(receivers()).actionBar(text)
        override fun playSound(sound: Sound) = CompositeAudience(receivers()).playSound(sound)
    }

    private class CompositeAudience(receivers: Iterable<LibraryAudience>) : LibraryAudience {
        private val receivers = receivers.filterDistinctByIdentity()
        override fun sendMessage(text: Component) = each { it.sendMessage(text) }
        override fun actionBar(text: Component) = each { it.actionBar(text) }
        override fun playSound(sound: Sound): Boolean {
            if (receivers.isEmpty()) return false
            var accepted = true
            receivers.forEach { receiver ->
                try { if (!receiver.playSound(sound)) accepted = false }
                catch (error: Throwable) { if (error is Error) throw error; accepted = false }
            }
            return accepted
        }
        private inline fun each(operation: (LibraryAudience) -> Unit) {
            receivers.forEach { receiver ->
                try { operation(receiver) } catch (error: Throwable) { if (error is Error) throw error }
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
            override fun playSound(sound: Sound) = false
        }

        fun Iterable<LibraryAudience>.filterDistinctByIdentity(): List<LibraryAudience> {
            val seen = Collections.newSetFromMap(IdentityHashMap<LibraryAudience, Boolean>())
            return filter(seen::add)
        }
    }
}
