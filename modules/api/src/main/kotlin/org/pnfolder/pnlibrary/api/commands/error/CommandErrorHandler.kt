package org.pnfolder.pnlibrary.api.commands.error

import org.pnfolder.pnlibrary.api.commands.CommandContext

/** Answers an error. `context` tells who ran which command; the sender can be messaged, played a sound, anything. */
fun interface CommandErrorHandler<E : CommandError> {
    /** Called with the error; throwing here is logged and the next level answers instead. */
    fun handle(context: CommandContext, error: E)
}
