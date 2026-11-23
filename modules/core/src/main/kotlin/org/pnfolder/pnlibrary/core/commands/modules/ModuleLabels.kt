package org.pnfolder.pnlibrary.core.commands.modules

import org.pnfolder.pnlibrary.api.chat.ChatPalette
import org.pnfolder.pnlibrary.api.plugin.ModuleContext
import org.pnfolder.pnlibrary.api.plugin.control.ModuleState
import org.pnfolder.pnlibrary.api.runtime.PnLibrary

/** Words and colours for module states, shared by `/pn modules` and the menu. */
object ModuleLabels {
    fun label(state: ModuleState): String = when (state) {
        ModuleState.ENABLED -> "работает"
        ModuleState.DISABLED -> "выключен"
        ModuleState.FAILED -> "ошибка"
    }

    fun color(state: ModuleState) = when (state) {
        ModuleState.ENABLED -> ChatPalette.OK
        ModuleState.DISABLED -> ChatPalette.FAINT
        ModuleState.FAILED -> ChatPalette.DANGER
    }

    /** What a module is called to a person: its own name from the metadata, then its id. */
    fun title(module: ModuleContext): String = module.metadata.name.ifBlank { module.id.value }

    /** Every module of every plugin with the text that names it in commands: `плагин:модуль`. */
    fun named(library: PnLibrary): List<Pair<String, ModuleContext>> =
        library.plugins.all().flatMap { plugin -> plugin.all().map { "${plugin.id.value}:${it.id.value}" to it } }.sortedBy { it.first }
}
