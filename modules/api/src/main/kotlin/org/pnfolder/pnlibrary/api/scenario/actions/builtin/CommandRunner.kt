package org.pnfolder.pnlibrary.api.scenario.actions.builtin

/** Who runs the command of a [CommandAction]. */
enum class CommandRunner {
    /** The player the scenario is for. */
    PLAYER,

    /** The console, with every permission. */
    CONSOLE,
}
