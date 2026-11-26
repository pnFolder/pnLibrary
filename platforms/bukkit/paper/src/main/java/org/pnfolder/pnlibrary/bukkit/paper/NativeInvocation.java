package org.pnfolder.pnlibrary.bukkit.paper;

import java.util.List;
import java.util.concurrent.CompletionStage;

import org.pnfolder.pnlibrary.api.commands.definition.CommandNode;

interface NativeInvocation<S> {
    void execute(S source, String input);

    CompletionStage<List<String>> suggest(S source, String input);

    default boolean allowed(S source, CommandNode node) {
        return true;
    }
}
