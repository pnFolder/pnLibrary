package ru.privatenull.pnlibrary.api.commands

import net.kyori.adventure.text.Component
import ru.privatenull.pnlibrary.api.audiences.AudienceSender

/** Platform-neutral command sender exposed to command handlers. */
interface CommandSender : AudienceSender {
    /** Stable identity used for cooldowns and audit records. */
    override val id: String

    /** Human-readable sender name. */
    override val name: String

    /** Whether this sender represents the server console. */
    override val isConsole: Boolean

    /** Returns whether this sender has [permission]. */
    override fun hasPermission(permission: String): Boolean

    /** Sends one Adventure component to this sender. */
    fun send(message: Component)

    override fun sendMessage(text: Component) = send(text)

    override fun actionBar(text: Component) = send(text)

    override fun playSound(sound: net.kyori.adventure.sound.Sound): Boolean = false
}
