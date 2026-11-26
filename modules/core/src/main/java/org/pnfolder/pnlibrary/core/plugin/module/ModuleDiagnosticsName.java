package org.pnfolder.pnlibrary.core.plugin.module;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

/**
 * Name under which a module appears in diagnostics and in the support archive: {@code plugin/module}, so the report reads
 * {@code plugins/pnclans/modules/war/} and {@code /pn debug pnClans} includes the plugin's modules.
 */
public final class ModuleDiagnosticsName {
    private ModuleDiagnosticsName() {
    }

    public static @NotNull String of(@NotNull PluginId plugin, @NotNull ModuleId module) {
        return plugin.getValue() + "/" + module.getValue();
    }
}
