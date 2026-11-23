package org.pnfolder.pnlibrary.spi.player

import net.kyori.adventure.audience.Audience
import org.pnfolder.pnlibrary.api.player.LibraryPlayer
import org.pnfolder.pnlibrary.api.sender.senderPointers
import java.util.UUID

/** Native audience lookup boundary implemented by each platform runtime. */
interface PlatformPlayerAdapter {
    /** Returns the native platform's console audience. */
    fun console(): Audience
    /** Resolves an online player by [uniqueId]. */
    fun player(uniqueId: UUID): LibraryPlayer?
    /** Adapts a platform-native sender object. */
    fun sender(native: Any): Audience?
    /** Returns a snapshot of players currently online. */
    fun onlinePlayers(): List<LibraryPlayer>
}

/** No-op audience adapter used by runtimes without native audience support. */
object UnsupportedPlatformPlayerAdapter : PlatformPlayerAdapter {
    private val console = object : Audience {
        override fun pointers() = senderPointers("Console") { false }
    }

    /** Returns a console that shows nothing and grants nothing. */
    override fun console(): Audience = console

    /** Always returns `null`. */
    override fun player(uniqueId: UUID): LibraryPlayer? = null

    /** Always returns `null`. */
    override fun sender(native: Any): Audience? = null

    /** Always returns an empty list. */
    override fun onlinePlayers(): List<LibraryPlayer> = emptyList()
}
