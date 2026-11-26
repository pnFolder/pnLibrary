package org.pnfolder.pnlibrary.bukkit.paper;

import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.pnfolder.pnlibrary.api.commands.definition.CommandNode;
import org.pnfolder.pnlibrary.api.logging.PnLogger;

/** Runs one queued command for the Brigadier tree: the portable router does the work, Paper only matches the words. */
final class PaperEntryInvocation implements NativeInvocation<CommandSourceStack> {
    private final PaperCommandEntry entry;
    private final Supplier<PnLogger> pnLogger;

    PaperEntryInvocation(PaperCommandEntry entry, Supplier<PnLogger> pnLogger) {
        this.entry = entry;
        this.pnLogger = pnLogger;
    }

    @Override
    public void execute(CommandSourceStack source, String input) {
        if (!entry.isActive()) {
            source.getSender().sendMessage("This command is currently unavailable.");
            return;
        }
        CommandLine line = CommandLine.of(input, false);
        entry.getExecute().execute(source.getSender(), line.getLabel(), line.getArguments().toArray(new String[0]));
    }

    @Override
    public boolean allowed(CommandSourceStack source, CommandNode node) {
        return PaperSenderAccess.allowed(source.getSender(), node);
    }

    @Override
    public CompletionStage<List<String>> suggest(CommandSourceStack source, String input) {
        CommandLine line = CommandLine.of(input, true);
        return entry.getSuggest()
            .suggest(source.getSender(), line.getLabel(), line.getArguments().toArray(new String[0]))
            .whenComplete((ignored, error) -> {
                // Tab runs on every keystroke, so one report per command is enough.
                if (error != null && entry.markSuggestionFailureReported()) {
                    pnLogger.get().error("Tab completion of /" + entry.getCommand().getName() + " failed", error);
                }
            });
    }
}
