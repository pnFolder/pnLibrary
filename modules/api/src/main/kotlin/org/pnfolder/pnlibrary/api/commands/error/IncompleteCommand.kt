package org.pnfolder.pnlibrary.api.commands.error

/** The command stopped before a part it needs. */
open class IncompleteCommand(usage: String) : BadUsage(usage)
