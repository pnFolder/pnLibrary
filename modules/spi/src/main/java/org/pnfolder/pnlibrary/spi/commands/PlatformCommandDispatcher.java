package org.pnfolder.pnlibrary.spi.commands;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;

import java.util.List;
import java.util.concurrent.CompletionStage;

/** Shared execution and completion dispatcher called by native delegates. */
public interface PlatformCommandDispatcher {
    /** Executes the resolved {@code command} with portable {@code context}. */
    @NotNull
    CompletionStage<Void> execute(@NotNull CommandDefinition command, @NotNull CommandContext context);

    /** Computes suggestions for {@code command} with portable {@code context}. */
    @NotNull
    CompletionStage<List<String>> suggest(@NotNull CommandDefinition command, @NotNull CommandContext context);
}
