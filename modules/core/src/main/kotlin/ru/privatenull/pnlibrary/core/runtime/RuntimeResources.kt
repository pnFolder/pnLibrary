package ru.privatenull.pnlibrary.core.runtime

import ru.privatenull.pnlibrary.api.commands.CommandBuilder
import ru.privatenull.pnlibrary.api.commands.CommandDefinition
import ru.privatenull.pnlibrary.api.commands.CommandRegistration
import ru.privatenull.pnlibrary.api.commands.command
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.api.tasks.TaskScope

/** Private runtime capabilities, deliberately absent from the consumer API facade. */
internal interface RuntimeResources {
    val runtimeTasks: TaskScope
    fun registerRuntimeCommand(command: CommandDefinition): CommandRegistration
}

/**
 * Native pnLibrary integration only. Consumer plugins obtain tasks through ModuleContext.tasks.
 * This bridge belongs to core, not to the published public API.
 */
fun PnLibrary.runtimeTaskScope(): TaskScope = (this as RuntimeResources).runtimeTasks

/** Registers a pnLibrary-owned command from core or a bundled native integration. */
fun PnLibrary.registerRuntimeCommand(command: CommandDefinition): CommandRegistration =
    (this as RuntimeResources).registerRuntimeCommand(command)

/** Builds a pnLibrary-owned command; consumer plugins use ModuleContext.registerCommand. */
fun PnLibrary.registerRuntimeCommand(
    name: String,
    configure: CommandBuilder.() -> Unit,
): CommandRegistration = registerRuntimeCommand(command(name, configure))
