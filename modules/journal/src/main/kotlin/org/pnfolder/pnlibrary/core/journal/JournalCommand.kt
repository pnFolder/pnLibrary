package org.pnfolder.pnlibrary.core.journal

import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.api.commands.ArgumentType
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.command

/**
 * `/pn events [info|warning|error|all] [page]`: the journal with repeats of one event folded into a single row.
 *
 * A row shows the last time, level, source, how many times it happened and a message that is shortened in
 * player chat and whole in the console. Hovering shows a summary; clicking opens the card
 * `/pn events view <id>` with the full text, first and last time and the stack trace, plus buttons for the
 * repeats (`/pn events times <id>`) and for copying a report to hand to the plugin author.
 * Requires `pnlibrary.events.view`; the console always may read it.
 */
fun journalCommand(journal: JournalStore, root: String = "/pn events"): CommandDefinition = command("events") {
    permission("pnlibrary.events.view")
    noPermissionMessage("Журнал событий доступен администраторам. Нужно право pnlibrary.events.view.")
    consoleBypassesPermission()
    // The router accepts only declared words, so every level, page and card id is declared here.
    executes { showList(it, journal, root) }
    listOf("all", "info", "warning", "error").forEach { level ->
        literal(level) {
            executes { showList(it, journal, root) }
            argument("page", ArgumentType.integer()) {
                executes { showList(it, journal, root) }
            }
        }
    }
    literal("view") {
        argument("id", ArgumentType.string()) {
            executes { showCard(it, journal, root, repeats = false, fullTrace = false) }
        }
    }
    literal("full") {
        argument("id", ArgumentType.string()) {
            executes { showCard(it, journal, root, repeats = false, fullTrace = true) }
        }
    }
    literal("times") {
        argument("id", ArgumentType.string()) {
            executes { showCard(it, journal, root, repeats = true, fullTrace = false) }
        }
    }
}

private fun showList(context: CommandContext, journal: JournalStore, root: String) =
    list(context.sender, ChatKit(ChatReader.of(context.sender)), journal, context.arguments, root)

private fun showCard(context: CommandContext, journal: JournalStore, root: String, repeats: Boolean, fullTrace: Boolean) =
    card(context.sender, ChatKit(ChatReader.of(context.sender)), journal, context.arguments.getOrNull(1), repeats, fullTrace, root)
