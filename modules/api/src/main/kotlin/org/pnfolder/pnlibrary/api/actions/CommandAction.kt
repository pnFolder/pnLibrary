package org.pnfolder.pnlibrary.api.actions

import org.pnfolder.pnlibrary.api.config.ConfigKey

/** Who runs the command of a [CommandAction]. */
enum class CommandRunner {
    /** The player that triggered the action. */
    PLAYER,

    /** The server console, with full permissions. */
    CONSOLE,
}

/**
 * Runs a server command.
 *
 * `[[player]]` and other `[[name]]` values are replaced in the text before it runs. If the
 * platform refuses the command, a warning is written to the action logger and execution continues.
 *
 * @property command command line without the leading slash
 * @property runner who runs the command
 */
data class CommandAction(
    val command: String = "",
    @field:ConfigKey("as")
    val runner: CommandRunner = CommandRunner.CONSOLE,
) : Action {
    override fun execute(context: ActionContext) {
        val line = context.format(command).trim().removePrefix("/")
        if (line.isEmpty()) return
        if (!context.player.runCommand(line, runner == CommandRunner.CONSOLE)) {
            context.logger.warning("Command action was not accepted by the current platform: $line")
        }
    }
}
