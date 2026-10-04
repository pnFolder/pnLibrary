package ru.privatenull.pnlibrary.spi.audiences

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import ru.privatenull.pnlibrary.api.audiences.AudienceSender
import ru.privatenull.pnlibrary.api.actions.LibraryPlayer
import java.util.UUID

/** Native audience lookup boundary implemented by each platform runtime. */
interface PlatformAudienceAdapter {
    /** Returns the native platform's console audience. */
    fun console(): AudienceSender
    /** Resolves an online player by [uniqueId]. */
    fun player(uniqueId: UUID): LibraryPlayer?
    /** Adapts a platform-native sender object. */
    fun sender(native: Any): AudienceSender?
    /** Returns a snapshot of players currently online. */
    fun onlinePlayers(): List<LibraryPlayer>
}

/** No-op audience adapter used by runtimes without native audience support. */
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
    /** Returns a no-op console audience. */
    override fun console() = console
    /** Always returns `null`. */
    override fun player(uniqueId: UUID): LibraryPlayer? = null
    /** Always returns `null`. */
    override fun sender(native: Any): AudienceSender? = null
    /** Always returns an empty list. */
    override fun onlinePlayers(): List<LibraryPlayer> = emptyList()
}
