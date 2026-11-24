package org.pnfolder.pnlibrary.spi.audience

import net.kyori.adventure.audience.Audience
import org.pnfolder.pnlibrary.api.audience.LibraryPlayer
import org.pnfolder.pnlibrary.api.audience.senderPointers
import java.util.UUID

/** Native audience lookup boundary implemented by each platform runtime. */
interface PlatformAudienceAdapter {
    /** Returns the native platform's console audience. */
    fun console(): Audience
    /** Resolves an online player by [uniqueId]. */
    fun player(uniqueId: UUID): LibraryPlayer?
    /** Adapts a platform-native sender object. */
    fun of(native: Any): Audience?
    /** Returns a snapshot of players currently online. */
    fun players(): List<LibraryPlayer>
}

/** No-op audience adapter used by runtimes without native audience support. */
object UnsupportedPlatformAudienceAdapter : PlatformAudienceAdapter {
    private val console = object : Audience {
        override fun pointers() = senderPointers("Console") { false }
    }

    /** Returns a console that shows nothing and grants nothing. */
    override fun console(): Audience = console

    /** Always returns `null`. */
    override fun player(uniqueId: UUID): LibraryPlayer? = null

    /** Always returns `null`. */
    override fun of(native: Any): Audience? = null

    /** Always returns an empty list. */
    override fun players(): List<LibraryPlayer> = emptyList()
}
