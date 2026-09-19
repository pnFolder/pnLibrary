package ru.privatenull.pnlibrary.spi.audiences

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import ru.privatenull.pnlibrary.api.audiences.AudienceSender
import ru.privatenull.pnlibrary.api.actions.LibraryPlayer
import java.util.UUID

/** Native audience lookup boundary implemented by each platform runtime. */
interface PlatformAudienceAdapter {
    fun console(): AudienceSender
    fun player(uniqueId: UUID): LibraryPlayer?
    fun sender(native: Any): AudienceSender?
    fun onlinePlayers(): List<LibraryPlayer>
}

object UnsupportedPlatformAudienceAdapter : PlatformAudienceAdapter {
    private val console = object : AudienceSender {
        override val id = "console"
        override val name = "Console"
        override val isConsole = true
        override fun hasPermission(permission: String) = false
        override fun sendMessage(text: Component) = Unit
        override fun actionBar(text: Component) = Unit
        override fun playSound(sound: Sound) = false
    }
    override fun console() = console
    override fun player(uniqueId: UUID): LibraryPlayer? = null
    override fun sender(native: Any): AudienceSender? = null
    override fun onlinePlayers(): List<LibraryPlayer> = emptyList()
}
