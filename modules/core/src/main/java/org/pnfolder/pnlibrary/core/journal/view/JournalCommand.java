package org.pnfolder.pnlibrary.core.journal.view;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.chat.ChatKit;
import org.pnfolder.pnlibrary.api.chat.ChatReader;
import org.pnfolder.pnlibrary.api.commands.CommandContext;
import org.pnfolder.pnlibrary.api.commands.definition.ArgumentType;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.core.journal.store.JournalStore;

import java.util.List;

/**
 * {@code /pn events [info|warning|error|all] [page]}: the journal with repeats of one event folded into a single row.
 *
 * <p>A row shows the last time, level, source, how many times it happened and a message that is shortened in player
 * chat and whole in the console. Hovering shows a summary; clicking opens the card {@code /pn events view <id>} with
 * the full text, first and last time and the stack trace, plus buttons for the repeats
 * ({@code /pn events times <id>}) and for copying a report to hand to the plugin author. Requires
 * {@code pnlibrary.events.view}; the console always may read it.
 */
public final class JournalCommand {
    private static final String DEFAULT_ROOT = "/pn events";

    private JournalCommand() {
    }

    public static @NotNull CommandDefinition create(@NotNull JournalStore journal) {
        return create(journal, DEFAULT_ROOT);
    }

    public static @NotNull CommandDefinition create(@NotNull JournalStore journal, @NotNull String root) {
        return CommandDefinition.command("events", command -> {
            command.permission("pnlibrary.events.view");
            command.noPermissionMessage("Журнал событий доступен администраторам. Нужно право pnlibrary.events.view.");
            command.consoleBypassesPermission();
            // The router accepts only declared words, so every level, page and card id is declared here.
            command.executes(context -> showList(context, journal, root));
            for (String level : new String[] {"all", "info", "warning", "error"}) {
                command.literal(level, node -> {
                    node.executes(context -> showList(context, journal, root));
                    node.argument("page", ArgumentType.integer(), page ->
                        page.executes(context -> showList(context, journal, root)));
                });
            }
            card(command, "view", journal, root, false, false);
            card(command, "full", journal, root, false, true);
            card(command, "times", journal, root, true, false);
        });
    }

    private static void card(org.pnfolder.pnlibrary.api.commands.definition.CommandBuilder command, String word,
                             JournalStore journal, String root, boolean repeats, boolean fullTrace) {
        command.literal(word, node -> node.argument("id", ArgumentType.string(), id ->
            id.executes(context -> showCard(context, journal, root, repeats, fullTrace))));
    }

    private static void showList(CommandContext context, JournalStore journal, String root) {
        JournalList.show(context.getSender(), new ChatKit(ChatReader.of(context.getSender())), journal,
            context.getArguments(), root);
    }

    private static void showCard(CommandContext context, JournalStore journal, String root, boolean repeats,
                                 boolean fullTrace) {
        List<String> arguments = context.getArguments();
        JournalCard.show(context.getSender(), new ChatKit(ChatReader.of(context.getSender())), journal,
            arguments.size() > 1 ? arguments.get(1) : null, repeats, fullTrace, root);
    }
}
