package ru.privatenull.pnlibrary.api.commands

import net.kyori.adventure.text.Component

/** Platform-neutral command sender exposed to command handlers. */
interface CommandSender {
    /** Stable identity used for cooldowns and audit records. */
    val id: String

    /** Human-readable sender name. */
    val name: String

    /** Whether this sender represents the server console. */
    val isConsole: Boolean

    /** Returns whether this sender has [permission]. */
    fun hasPermission(permission: String): Boolean

    /** Sends one Adventure component to this sender. */
    fun send(message: Component)
}
