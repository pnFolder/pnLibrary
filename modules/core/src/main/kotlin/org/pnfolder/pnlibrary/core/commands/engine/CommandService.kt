package org.pnfolder.pnlibrary.core.commands.engine

import org.pnfolder.pnlibrary.api.commands.CommandRegistration
import org.pnfolder.pnlibrary.api.commands.definition.CommandBuilder
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.definition.command

/**
 * Registers portable commands and binds their lifecycle to an owner.
 * Registration, dispatch lookup, owner removal, and shutdown are safe from arbitrary threads.
 * Command callbacks execute in the context selected by the platform dispatcher.
 */
internal interface CommandService {
    /**
     * Registers [command] and binds its lifecycle to [owner]; [feedback] holds what the owning module wants every
     * one of its commands to answer when a sender is denied or types the command wrongly.
     */
    fun register(owner: Any, command: CommandDefinition, feedback: CommandFeedbackScope? = null): CommandRegistration

    /**
     * Builds and registers a command while keeping the command-definition ceremony
     * out of the call site.
     */
    fun register(owner: Any, name: String, configure: CommandBuilder.() -> Unit): CommandRegistration =
        register(owner, command(name, configure))

    fun unregisterOwner(owner: Any)
}
