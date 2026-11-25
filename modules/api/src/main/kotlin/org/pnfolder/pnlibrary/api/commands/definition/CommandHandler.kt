package org.pnfolder.pnlibrary.api.commands.definition

import org.pnfolder.pnlibrary.api.commands.CommandContext
import java.util.concurrent.CompletionStage

/** Asynchronous execution contract stored by an immutable command definition. */
fun interface CommandHandler {
    /** Executes a command invocation and completes when handling has finished. */
    fun execute(context: CommandContext): CompletionStage<Void>
}
