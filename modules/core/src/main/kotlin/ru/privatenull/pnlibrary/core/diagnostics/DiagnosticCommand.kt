package ru.privatenull.pnlibrary.core.diagnostics

import net.kyori.adventure.text.Component
import ru.privatenull.pnlibrary.api.commands.CommandContext
import ru.privatenull.pnlibrary.api.commands.CommandDefinition
import ru.privatenull.pnlibrary.api.commands.command
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
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
            executor.execute(
                arguments = context.arguments.toTypedArray(),
                prefixed = false,
                requesterId = context.sender.id,
                recipient = context.sender,
            ) { event -> context.sender.send(event.toComponent()) }
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

/** Renders diagnostics state once for every native platform. */
internal fun DiagnosticCommandEvent.toComponent(): Component = Component.text(
    when (this) {
        DiagnosticCommandEvent.InvalidUsage ->
            "/pndebug [all|plugin] [--full|--config|--logs] [--local]"
        is DiagnosticCommandEvent.CoolingDown ->
            "Wait ${seconds}s before creating another report."
        is DiagnosticCommandEvent.Started ->
            "Collecting diagnostic report for $target..."
        is DiagnosticCommandEvent.Completed -> {
            val output = report.uploadedUrl ?: report.localFile.toString()
            "Report ready: $output" +
                (report.uploadError?.let { " (upload failed; local report kept: $it)" } ?: "")
        }
        is DiagnosticCommandEvent.Failed -> "Report failed: $message"
    },
)
