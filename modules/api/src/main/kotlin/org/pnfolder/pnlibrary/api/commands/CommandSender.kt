package org.pnfolder.pnlibrary.api.commands

import net.kyori.adventure.sound.Sound
import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.audiences.AudienceSender

/** Platform-neutral command sender exposed to command handlers. */
interface CommandSender : AudienceSender {
    /** Stable identity used for cooldowns and audit records. */
    override val id: String

    /** Human-readable sender name. */
    override val name: String

    /** Whether this sender represents the server console. */
    override val isConsole: Boolean

    /**
     * Whether clickable and hoverable chat components reach this sender intact.
     *
     * False for the console, and for players on a server whose chat transport fell back to plain text. Senders
     * for which it is false should be shown text that does not rely on buttons.
     */
    val supportsInteraction: Boolean
        get() = !isConsole

    /** Returns whether this sender has [permission]. */
    override fun hasPermission(permission: String): Boolean

    /** Sends one Adventure component to this sender. */
    fun send(message: Component)

    /** Sends [text] as a normal chat message. */
    override fun sendMessage(text: Component) = send(text)

    /** Falls back to chat output when the platform has no specialized action bar adapter. */
    override fun actionBar(text: Component) = send(text)

    /** Returns `false` because generic command senders cannot play sounds. */
    override fun playSound(sound: Sound): Boolean = false
}
