package org.pnfolder.pnlibrary.bukkit.integration;

import org.bukkit.plugin.java.JavaPlugin;
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntimeHost;

/** What an integration needs: the plugin and the running library. */
final class IntegrationContext {
    private final JavaPlugin plugin;
    private final PnLibraryRuntimeHost runtime;

    IntegrationContext(JavaPlugin plugin, PnLibraryRuntimeHost runtime) {
        this.plugin = plugin;
        this.runtime = runtime;
    }

    JavaPlugin getPlugin() {
        return plugin;
    }

    PnLibraryRuntimeHost getRuntime() {
        return runtime;
    }
}
