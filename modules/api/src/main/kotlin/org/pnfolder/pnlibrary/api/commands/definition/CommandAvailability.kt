package org.pnfolder.pnlibrary.api.commands.definition

import org.pnfolder.pnlibrary.api.commands.CommandContext

/** Synchronous access rule evaluated with values parsed before this node. */
fun interface CommandAvailability {
    /** Returns whether this node is visible and executable for [context]. */
    fun isAvailable(context: CommandContext): Boolean
}
