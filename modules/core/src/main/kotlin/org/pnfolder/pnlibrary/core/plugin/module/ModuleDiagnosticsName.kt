package org.pnfolder.pnlibrary.core.plugin.module

import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginId

/**
 * Name under which a module appears in diagnostics and in the support archive: `plugin/module`, so the report reads
 * `plugins/pnclans/modules/war/` and `/pn debug pnClans` includes the plugin's modules.
 */
internal fun moduleDiagnosticsName(plugin: PluginId, module: ModuleId): String = "${plugin.value}/${module.value}"
