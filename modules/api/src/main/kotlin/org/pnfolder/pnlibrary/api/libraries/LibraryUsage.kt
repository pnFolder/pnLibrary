package org.pnfolder.pnlibrary.api.libraries

import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginId

/**
 * Who registered a library: a plugin for all its modules, or one module.
 *
 * @property library the library
 * @property plugin the plugin that owns it
 * @property module the module that registered it, or `null` when the whole plugin did
 */
data class LibraryUsage(val library: ModuleLibrary, val plugin: PluginId, val module: ModuleId?) {
    /** `plugin` or `plugin:module`, the text used in commands. */
    val owner: String get() = if (module == null) plugin.value else "${plugin.value}:${module.value}"
}
