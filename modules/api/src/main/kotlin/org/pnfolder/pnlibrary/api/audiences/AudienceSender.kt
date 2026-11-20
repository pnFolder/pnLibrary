package org.pnfolder.pnlibrary.api.audiences

/**
 * A [LibraryAudience] that has an identity and permissions: the console or a player.
 *
 * This is the base of every sender in the library, the way `CommandSender` is in Bukkit:
 *
 * ```
 * LibraryAudience            what can be shown
 *  └ AudienceSender          who it is and what they may do
 *     ├ CommandSender        who ran a command
 *     └ LibraryPlayer        an online player
 *        └ NetworkPlayer     a player seen through a proxy
 * ```
 *
 * ```kotlin
 * fun greet(sender: AudienceSender) {
 *     if (sender.hasPermission("shop.use")) sender.sendMessage(Component.text("Welcome, ${sender.name}"))
 * }
 * ```
 */
interface AudienceSender : LibraryAudience {
    /** Stable platform-independent identifier: the player UUID as text, or a fixed value for the console. */
    val id: String

    /** Current human-readable name. */
    val name: String

    /** Whether this sender is the server console. */
    val isConsole: Boolean

    /** Whether this sender is a connected player; the opposite of [isConsole]. */
    val isPlayer: Boolean get() = !isConsole

    /** Returns whether the active platform grants this sender [permission]. */
    fun hasPermission(permission: String): Boolean
}
