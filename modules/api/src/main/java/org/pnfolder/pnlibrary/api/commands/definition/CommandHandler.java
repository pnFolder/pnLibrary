package org.pnfolder.pnlibrary.api.commands.definition;

import java.util.concurrent.CompletionStage;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.commands.CommandContext;

/** Asynchronous execution contract stored by an immutable command definition. */
@FunctionalInterface
public interface CommandHandler {
    /** Executes a command invocation and completes when handling has finished. */
    @NotNull
    CompletionStage<Void> execute(@NotNull CommandContext context);
}
