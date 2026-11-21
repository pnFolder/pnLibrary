package org.pnfolder.pnlibrary.api.audiences

import org.pnfolder.pnlibrary.api.audiences.player.LibraryPlayer
import java.util.UUID

/**
 * Finds and combines audiences of the active platform: the console, players, and groups of them.
 *
 * ```kotlin
 * val audiences = library.audiences
 * audiences.console().sendMessage(Component.text("Started"))
 * audiences.player(uuid)?.sendMessage(Component.text("Hello"))
 * audiences.all().actionBar(Component.text("Restart in 5 minutes"))
 * ```
 */
interface AudienceService {
    /** Returns the server console. */
    fun console(): AudienceSender

    /** Returns the online player with [uniqueId], or `null` when absent. */
    fun player(uniqueId: UUID): LibraryPlayer?

    /** Adapts a platform-native sender object (such as a Bukkit `CommandSender`), or `null` when unsupported. */
    fun sender(native: Any): AudienceSender?

    /** Returns a snapshot of all players currently online. */
    fun onlinePlayers(): List<LibraryPlayer>

    /** Returns a dynamic audience of every online player, re-evaluated on each operation. */
    fun all(): LibraryAudience

    /** Combines [audiences] into one that forwards every operation to each of them. */
    fun combine(audiences: Iterable<LibraryAudience>): LibraryAudience
}
