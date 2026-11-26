package org.pnfolder.pnlibrary.api.commands.definition;

import java.util.List;
import java.util.concurrent.CompletionStage;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.commands.CommandContext;

/** Asynchronous suggestion contract stored by an immutable command definition. */
@FunctionalInterface
public interface SuggestionHandler {
    /** Computes completion suggestions for the supplied parsing {@code context}. */
    @NotNull
    CompletionStage<List<String>> suggest(@NotNull CommandContext context);
}
