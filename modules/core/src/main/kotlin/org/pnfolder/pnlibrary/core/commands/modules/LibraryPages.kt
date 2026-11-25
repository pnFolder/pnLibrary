package org.pnfolder.pnlibrary.core.commands.modules

import org.pnfolder.pnlibrary.api.chat.ChatKit
import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.chat.ChatReader
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.libraries.state.LibraryState
import org.pnfolder.pnlibrary.api.libraries.ModuleLibraries
import org.pnfolder.pnlibrary.api.runtime.PnLibrary

/** The `libs` list and the `lib enable|disable|retry` switches of `/pn modules`: every library of every plugin and module. */
internal class LibraryPages(private val library: PnLibrary) {
    private fun kit(context: CommandContext) = ChatKit(ChatReader.of(context.sender))

    /** The libraries of a plugin (`plugin`) or of one of its modules (`plugin:module`). */
    private fun holderOf(owner: String): ModuleLibraries? {
        for (plugin in library.plugins.all()) {
            if (plugin.id.value.equals(owner, true)) return plugin.libraries
            for (module in plugin.all()) if ("${plugin.id.value}:${module.id.value}".equals(owner, true)) return module.libraries
        }
        return null
    }

    fun changeLibrary(context: CommandContext, action: String, owner: String, coordinates: String) {
        val kit = kit(context)
        val holder = holderOf(owner)
        if (holder == null) { context.sender.sendMessage(kit.note("Нет плагина или модуля «$owner». Список: /pn modules libs", ChatPalette.DANGER)); return }
        val found = holder.statuses.firstOrNull { it.library.coordinates.equals(coordinates, true) }?.library
        if (found == null) {
            context.sender.sendMessage(kit.note("У $owner нет библиотеки «$coordinates». Список: /pn modules libs $owner", ChatPalette.DANGER))
            return
        }
        val status = when (action) {
            "enable" -> holder.setAutoLoad(found, true)
            "disable" -> holder.setAutoLoad(found, false)
            else -> holder.retry(found)
        }
        val result = when {
            status.restartRequired -> "выключена: она уже используется, пропадёт после перезапуска сервера" to ChatPalette.WARN
            status.state == LibraryState.LOADED -> "загружена" to ChatPalette.OK
            status.state == LibraryState.DISABLED -> "выключена, при запуске сервера не загружается" to ChatPalette.WARN
            status.state == LibraryState.FAILED -> "ошибка: ${status.error}" to ChatPalette.DANGER
            else -> "не нужна на этой платформе" to ChatPalette.FAINT
        }
        context.sender.sendMessage(kit.text("${found.title} ${found.version}: ", ChatPalette.TEXT).append(kit.text(result.first, result.second)))
    }

    fun libs(context: CommandContext, owner: String?) {
        val kit = kit(context)
        val rows = LibraryRow.all(library).filter { owner == null || it.belongsTo(owner) }
        val lines = mutableListOf(kit.blank(), kit.header("Библиотеки", kit.text("${rows.size}", ChatPalette.LABEL)))
        if (rows.isEmpty()) lines += kit.note(if (owner == null) "Плагины не подключили библиотек." else "У $owner нет библиотек.", ChatPalette.FAINT)
        rows.groupBy { it.owner }.forEach { (who, own) ->
            lines += kit.text(" $who", ChatPalette.ACCENT)
            own.forEach { row ->
                var line = kit.text("    ${row.title}", ChatPalette.TEXT).append(kit.text(" ${row.version}", ChatPalette.SOFT))
                        .append(kit.dot())
                        .append(kit.text(when (row.scope) { "ISOLATED" -> "отдельно"; "LIBRARY" -> "в pnLibrary"; else -> "в плагине" }, ChatPalette.FAINT))
                if (row.size >= 0) line = line.append(kit.dot()).append(kit.text(DiskUsage.format(row.size), ChatPalette.SOFT))
                line = line.append(kit.dot()).append(
                    when (row.trust) {
                        LibraryRow.GIVEN -> kit.text("сумма задана", ChatPalette.OK)
                        else -> kit.text("jar не проверяется", ChatPalette.WARN)
                    },
                )
                if (!row.required) line = line.append(kit.dot()).append(kit.text("необязательная", ChatPalette.FAINT))
                line = when {
                    row.state == "DISABLED" -> line.append(kit.dot()).append(kit.text("выключена", ChatPalette.WARN))
                    row.state == "FAILED" -> line.append(kit.dot()).append(kit.text("ошибка: ${row.error}", ChatPalette.DANGER))
                    row.restartRequired -> line.append(kit.dot()).append(kit.text("выключится после перезапуска", ChatPalette.WARN))
                    else -> line
                }
                if (row.conflict) line = line.append(kit.dot()).append(kit.text("другая версия у другого плагина", ChatPalette.DANGER))
                lines += line
                row.description?.let { lines += kit.text("      $it", ChatPalette.LABEL) }
                row.place?.let { lines += kit.text("      лежит: $it", ChatPalette.FAINT) }
            }
        }
        lines.forEach(context.sender::send)
    }
}
