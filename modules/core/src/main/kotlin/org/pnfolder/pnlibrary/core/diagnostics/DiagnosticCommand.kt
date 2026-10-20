package org.pnfolder.pnlibrary.core.diagnostics

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.command
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticReport
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatNotice
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.api.chat.ChatTone
import java.util.Locale

/** Builds pnLibrary's diagnostics command without depending on a native command API. */
internal fun diagnosticCommand(library: PnLibrary): CommandDefinition {
    val executor = DiagnosticCommandExecutor(library)
    return command("pndebug") {
        aliases("pnlib")
        permission("pnlibrary.debug")
        consoleBypassesPermission()
        suggests { context -> diagnosticSuggestions(library, context) }
        executes { context ->
            val kit = ChatKit(ChatReader.of(context.sender))
            executor.execute(
                arguments = context.arguments.toTypedArray(),
                prefixed = false,
                requesterId = context.sender.id,
                recipient = context.sender,
            ) { event -> event.render(kit).forEach(context.sender::send) }
        }
    }
}

private fun diagnosticSuggestions(library: PnLibrary, context: CommandContext): List<String> {
    val prefix = context.currentInput.lowercase(Locale.ROOT)
    return buildList {
        add("all")
        library.plugins.all().flatMapTo(this) { plugin ->
            plugin.all().map { it.key.value }
        }
        add("--full")
        add("--config")
        add("--logs")
        add("--local")
    }.distinct().filter { it.lowercase(Locale.ROOT).startsWith(prefix) }
}

/** Renders diagnostics state once for every native platform, as notices in the shared chat style. */
internal fun DiagnosticCommandEvent.render(kit: ChatKit): List<Component> = when (this) {
    DiagnosticCommandEvent.InvalidUsage ->
        kit.notice(ChatNotice(ChatTone.WARNING, "Неизвестные аргументы.", "/pndebug [all|плагин] [--full|--config|--logs] [--local]"))
    is DiagnosticCommandEvent.CoolingDown ->
        kit.notice(ChatNotice(ChatTone.WARNING, "Подождите $seconds с перед следующим отчётом."))
    is DiagnosticCommandEvent.Started ->
        kit.notice(ChatNotice(ChatTone.PROGRESS, "Собираю диагностический отчёт: $target…"))
    is DiagnosticCommandEvent.Completed -> completed(kit, report)
    is DiagnosticCommandEvent.Failed ->
        kit.notice(ChatNotice(ChatTone.FAILURE, "Отчёт не создан.", message))
}

/** A finished report: its link with a button to open it, or the local file and why the upload failed. */
private fun completed(kit: ChatKit, report: DiagnosticReport): List<Component> {
    val link = report.uploadedUrl
    val error = report.uploadError
    return when {
        link != null -> kit.notice(ChatNotice(ChatTone.SUCCESS, "Отчёт готов.", link)) +
            listOfNotNull(kit.actions(kit.linkButton("Открыть отчёт", link, "Открыть отчёт в браузере", ChatPalette.INFO)))
        error != null -> kit.notice(
            ChatNotice(ChatTone.WARNING, "Отчёт сохранён только локально: загрузить не удалось.", "${report.localFile} · $error"),
        )
        else -> kit.notice(ChatNotice(ChatTone.SUCCESS, "Отчёт сохранён.", report.localFile.toString()))
    }
}
