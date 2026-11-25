package org.pnfolder.pnlibrary.api.audience

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.audience.ForwardingAudience
import java.util.UUID

/**
 * Everyone the library can reach on this server: the console and every online player. It is an Adventure [Audience]
 * itself, so a message sent to it goes to all of them, and Adventure's own `filterAudience` narrows it. Each receiver is
 * asked again on every operation, and one that fails does not stop the others.
 *
 * ```kotlin
 * val audiences = library.audiences
 * audiences.sendMessage(Component.text("The console and every player read this"))
 * audiences.console().sendMessage(Component.text("Started"))
 * audiences.player(uuid)?.sendMessage(Component.text("Hello"))
 * audiences.players().forEach { it.sendActionBar(Component.text("Restart in 5 minutes")) }
 * audiences.filterAudience { it.hasPermission("shop.admin") }.sendMessage(Component.text("Shop reloaded"))
 * ```
 */
interface Audiences : ForwardingAudience {
    /** The server console. */
    fun console(): Console

    /** A snapshot of all players currently online. */
    fun players(): List<OnlinePlayer>

    /** The online player with [uniqueId], or `null` when absent. */
    fun player(uniqueId: UUID): OnlinePlayer?

    /** The online player named [name], ignoring case, or `null` when absent. */
    fun player(name: String): OnlinePlayer? = players().firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Adapts a platform-native sender object (such as a Bukkit `CommandSender`), or `null` when unsupported. */
    fun of(native: Any): Audience?
}
