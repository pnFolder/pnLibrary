package org.pnfolder.pnlibrary.api.audience

import net.kyori.adventure.audience.Audience
import java.util.UUID

/**
 * Finds the audiences of the active platform: the console, players, and live groups of them. Every group is asked
 * again on each operation, and one receiver that fails does not stop the others.
 *
 * ```kotlin
 * val audiences = library.audiences
 * audiences.console().sendMessage(Component.text("Started"))
 * audiences.player(uuid)?.sendMessage(Component.text("Hello"))
 * audiences.players().forEach { it.sendActionBar(Component.text("Restart in 5 minutes")) }
 * audiences.withPermission("shop.admin").sendMessage(Component.text("Shop reloaded"))
 * audiences.filter { it.name.startsWith("A") }.sendMessage(Component.text("Hi"))
 * audiences.server().sendMessage(Component.text("The console and every player read this"))
 * ```
 */
interface Audiences {
    /** Returns the server console. */
    fun console(): Audience

    /** Returns a dynamic audience of the console and every online player, like the server itself in Adventure. */
    fun server(): Audience

    /** Returns a snapshot of all players currently online. */
    fun players(): List<LibraryPlayer>

    /** Returns the online player with [uniqueId], or `null` when absent. */
    fun player(uniqueId: UUID): LibraryPlayer?

    /** Returns the online player named [name], ignoring case, or `null` when absent. */
    fun player(name: String): LibraryPlayer? = players().firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Returns a dynamic audience of the online players accepted by [predicate]. */
    fun filter(predicate: (LibraryPlayer) -> Boolean): Audience

    /** Returns a dynamic audience of the online players who hold [permission]. */
    fun withPermission(permission: String): Audience = filter { it.hasPermission(permission) }

    /** Adapts a platform-native sender object (such as a Bukkit `CommandSender`), or `null` when unsupported. */
    fun of(native: Any): Audience?
}
