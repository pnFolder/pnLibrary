package ru.privatenull.pnlibrary.api.commands

/**
 * Registers portable commands and binds their lifecycle to an owner.
 * Registration, dispatch lookup, owner removal, and shutdown are safe from arbitrary threads.
 * Command callbacks execute in the context selected by the platform dispatcher.
 */
interface CommandService {
    /** Registers [command] and binds its lifecycle to [owner]. */
    fun register(owner: Any, command: CommandDefinition): CommandRegistration

    /**
     * Builds and registers a command while keeping the command-definition ceremony
     * out of the call site.
     */
    fun register(owner: Any, name: String, configure: CommandBuilder.() -> Unit): CommandRegistration =
        register(owner, command(name, configure))

    /** Closes every command registration belonging to [owner]. */
    fun unregisterOwner(owner: Any)
}

/** Closeable handle for one live native command registration. */
interface CommandRegistration : AutoCloseable {
    /** Portable definition represented by this registration. */
    val command: CommandDefinition
    /** Whether this registration has already been closed. */
    val isClosed: Boolean
    /** Removes the native command and releases this registration. */
    override fun close()
}
