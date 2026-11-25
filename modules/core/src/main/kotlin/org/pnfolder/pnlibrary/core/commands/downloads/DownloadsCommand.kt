package org.pnfolder.pnlibrary.core.commands.downloads

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.definition.command
import org.pnfolder.pnlibrary.api.downloads.DownloadSnapshot
import org.pnfolder.pnlibrary.api.plugin.PluginRegistry

private const val PERMISSION = "pnlibrary.downloads"

/**
 * `/pn downloads` shows the files plugins asked pnLibrary to download, with their state; `/pn downloads now` starts all of
 * them. Requires `pnlibrary.downloads`; the console always may. Missing plugins are in `/pn deps`.
 */
internal fun downloadsCommand(plugins: () -> PluginRegistry): CommandDefinition = command("downloads") {
    permission(PERMISSION)
    noPermissionMessage("Загрузки доступны администраторам. Нужно право $PERMISSION.")
    consoleBypassesPermission()
    executes { context ->
        val all = declaredDownloads(plugins())
        context.sender.sendMessage(Component.text("Загрузки: ${all.size}", ChatPalette.ACCENT))
        all.forEach { context.sender.sendMessage(row(it)) }
        context.sender.sendMessage(Component.text("Запустить все: /pn downloads now", ChatPalette.FAINT))
    }
    literal("now") {
        executes { context ->
            val started = plugins().all().flatMap { it.all() }.mapNotNull { it.downloads }.onEach { it.downloadNow() }.size
            context.sender.sendMessage(Component.text("Запущено наборов загрузок: $started", ChatPalette.TEXT))
        }
    }
}

/** One declared download: who asked (`plugin/module`) and its state. */
internal data class DeclaredDownload(val owner: String, val snapshot: DownloadSnapshot)

internal fun declaredDownloads(plugins: PluginRegistry): List<DeclaredDownload> =
    plugins.all().flatMap { plugin ->
        plugin.all().flatMap { module ->
            module.downloads?.snapshots().orEmpty().map { DeclaredDownload("${plugin.id.value}/${module.id.value}", it) }
        }
    }

private fun row(download: DeclaredDownload): Component {
    val note = download.snapshot.message?.let { "  $it" }.orEmpty()
    return Component.text(download.snapshot.key, ChatPalette.TEXT)
        .append(Component.text("  ${download.owner}  ${download.snapshot.state.name.lowercase()}$note", ChatPalette.SOFT))
}
