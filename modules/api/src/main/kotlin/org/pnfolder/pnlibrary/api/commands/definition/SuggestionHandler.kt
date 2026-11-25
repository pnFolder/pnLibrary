package org.pnfolder.pnlibrary.api.commands.definition

import org.pnfolder.pnlibrary.api.commands.CommandContext
import java.util.concurrent.CompletionStage

/** Asynchronous suggestion contract stored by an immutable command definition. */
fun interface SuggestionHandler {
    /** Computes completion suggestions for the supplied parsing [context]. */
    fun suggest(context: CommandContext): CompletionStage<List<String>>
}
