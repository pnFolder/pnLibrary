package ru.privatenull.pnlibrary.core.commands

import ru.privatenull.pnlibrary.api.commands.CommandBuilder
import ru.privatenull.pnlibrary.api.commands.CommandDefinition
import ru.privatenull.pnlibrary.api.commands.CommandRegistration
import ru.privatenull.pnlibrary.api.commands.command

/**
 * Registers portable commands and binds their lifecycle to an owner.
 * Registration, dispatch lookup, owner removal, and shutdown are safe from arbitrary threads.
 * Command callbacks execute in the context selected by the platform dispatcher.
 */
internal interface CommandService {
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
