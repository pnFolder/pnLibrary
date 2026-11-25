package org.pnfolder.pnlibrary.api.commands.error

/**
 * Stops the command with [error]. The library answers it through the five levels like any other error, so the
 * command does not print anything itself. Java callers may throw this directly.
 *
 * @property error the error to answer with
 */
class CommandException(val error: CommandError) : RuntimeException(error.javaClass.simpleName, null, false, false)
