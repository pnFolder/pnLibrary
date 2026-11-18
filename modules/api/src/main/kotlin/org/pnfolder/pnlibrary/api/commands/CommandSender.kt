package org.pnfolder.pnlibrary.api.commands

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.audiences.AudienceSender
import org.pnfolder.pnlibrary.api.audiences.Delivery

/**
 * Whoever ran a command: the console or a player. It is an [AudienceSender] with output suited to commands.
 *
 * ```kotlin
 * command("ping") {
 *     execute { ctx -> ctx.sender.send(Component.text("pong")) }
 * }
 * ```
 */
interface CommandSender : AudienceSender {
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

    override fun sendMessage(text: Component) = send(text)

    // Falls back to chat when the platform has no action bar for generic senders.
    override fun actionBar(text: Component) = send(text)

    // Generic command senders cannot play sounds.
    override fun playSound(sound: Sound): Delivery = Delivery.Unsupported
}
