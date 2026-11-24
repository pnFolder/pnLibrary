package org.pnfolder.pnlibrary.api.commands

import net.kyori.adventure.audience.Audience
import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.audience.isConsole

/**
 * Whoever ran a command: the console or a player. It is an Adventure [Audience]; its name, permissions and kind are
 * read with `name`, `hasPermission` and `isConsole` from `org.pnfolder.pnlibrary.api.audience`.
 *
 * ```kotlin
 * command("ping") {
 *     execute { ctx -> ctx.sender.send(Component.text("pong")) }
 * }
 * ```
 */
interface CommandSender : Audience {
    /**
     * Whether clickable and hoverable chat components reach this sender intact.
     *
     * `false` for the console, and for players on a server whose chat fell back to plain text; show such senders
     * text that does not rely on buttons.
     */
    val supportsInteraction: Boolean
        get() = !isConsole

    /** Sends one Adventure component to this sender. */
    fun send(message: Component)

    override fun sendMessage(message: Component) = send(message)

    // Falls back to chat when the platform has no action bar for generic senders.
    override fun sendActionBar(message: Component) = send(message)
}
