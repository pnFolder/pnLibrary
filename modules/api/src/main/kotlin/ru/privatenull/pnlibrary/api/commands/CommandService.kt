package ru.privatenull.pnlibrary.api.commands

/**
 * Registers portable commands and binds their lifecycle to an owner.
 * Registration, dispatch lookup, owner removal, and shutdown are safe from arbitrary threads.
 * Command callbacks execute in the context selected by the platform dispatcher.
 */
interface CommandService {
    fun register(owner: Any, command: CommandDefinition): CommandRegistration
    fun unregisterOwner(owner: Any)
}

/** Closeable handle for one live native command registration. */
interface CommandRegistration : AutoCloseable {
    val command: CommandDefinition
    val isClosed: Boolean
    override fun close()
}
