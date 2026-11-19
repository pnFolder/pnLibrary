package org.pnfolder.pnlibrary.core.menu.data

import org.pnfolder.pnlibrary.api.plugin.ModuleContext
import org.pnfolder.pnlibrary.api.plugin.control.ModuleState
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.core.commands.modules.ModuleLabels

/** One module of a plugin as the menu sees it; plain values, so it also crosses to another server. */
data class ModuleInfo(
    val key: String,
    val plugin: String,
    val title: String,
    val state: ModuleState,
    val autoStart: Boolean,
    val controllable: Boolean,
    val error: String?,
) {
    companion object {
        /** What can be done to a module; the words are the ones `/pn modules` uses. */
        val ACTIONS = listOf("enable", "disable", "restart", "reload", "auto-on", "auto-off")

        fun of(key: String, module: ModuleContext): ModuleInfo = ModuleInfo(
            key = key,
            plugin = module.metadata.name,
            title = ModuleLabels.title(module),
            state = module.control.state,
            autoStart = module.control.autoStart,
            controllable = module.control.isControllable,
            error = module.control.lastError?.let { it.message ?: it.javaClass.simpleName },
        )

        fun all(library: PnLibrary): List<ModuleInfo> =
            ModuleLabels.named(library).map { (key, module) -> of(key, module) }

        /** Runs [action] on the module [key] and returns how it looks afterwards. */
        fun apply(library: PnLibrary, key: String, action: String): ModuleInfo {
            val module = ModuleLabels.named(library).firstOrNull { it.first == key }?.second
                ?: throw IllegalArgumentException("Модуль $key не найден")
            require(action in ACTIONS) { "Неизвестное действие: $action" }
            val control = module.control
            when (action) {
                "enable" -> control.enable()
                "disable" -> control.disable()
                "restart" -> control.restart()
                "reload" -> control.reload()
                "auto-on" -> control.setAutoStart(true)
                else -> control.setAutoStart(false)
            }
            return of(key, module)
        }
    }
}
