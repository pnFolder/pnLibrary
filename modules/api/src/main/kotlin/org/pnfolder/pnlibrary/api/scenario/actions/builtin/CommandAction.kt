package org.pnfolder.pnlibrary.api.scenario.actions.builtin

import org.pnfolder.pnlibrary.api.config.ConfigKey
import org.pnfolder.pnlibrary.api.scenario.actions.Action
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult

/**
 * Runs a server command. A command the platform refuses fails the scenario, because later steps usually
 * depend on it.
 *
 * @property command command line without the leading slash; placeholders are filled in
 * @property runner who runs it
 */
data class CommandAction(
    val command: String = "",
    @field:ConfigKey("as")
    val runner: CommandRunner = CommandRunner.CONSOLE,
) : Action {
    override fun run(context: ActionContext): ActionResult {
        val line = context.format(command).trim().removePrefix("/")
        if (line.isEmpty()) return ActionResult.Done
        val player = context.player
        val delivery = if (runner == CommandRunner.CONSOLE) player.runConsoleCommand(line) else player.runCommand(line)
        return if (delivery.isDelivered) {
            ActionResult.Done
        } else {
            ActionResult.Failed("command.refused", "The platform did not accept the command '$line'")
        }
    }
}
