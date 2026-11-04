package org.pnfolder.pnlibrary.core.plugin

import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.CommandRegistration
import org.pnfolder.pnlibrary.core.commands.CommandFeedbackScope
import org.pnfolder.pnlibrary.core.commands.CommandService

/** The commands one module registered, so closing the module takes them all back. */
internal class ModuleCommands(
    private val owner: Any,
    private val commands: CommandService?,
    private val feedback: CommandFeedbackScope,
    private val isClosed: () -> Boolean,
) {
    private val registrations = mutableListOf<CommandRegistration>()

    fun register(command: CommandDefinition): CommandRegistration = synchronized(registrations) {
        check(!isClosed()) { "Module is closed" }
        val registration = checkNotNull(commands) { "Commands are unavailable" }.register(owner, command, feedback)
        if (isClosed()) {
            registration.close()
            error("Module is closed")
        }
        registrations.removeAll { it.isClosed }
        registrations.add(registration)
        registration
    }

    /** Hands over every registration still held and forgets them. */
    fun drain(): List<CommandRegistration> = synchronized(registrations) {
        registrations.toList().also { registrations.clear() }
    }
}
