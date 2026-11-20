package org.pnfolder.pnlibrary.config

import org.pnfolder.pnlibrary.api.config.ConfigScope
import org.pnfolder.pnlibrary.api.config.ConfigurationService
import org.pnfolder.pnlibrary.api.plugin.ModuleId
import org.pnfolder.pnlibrary.api.plugin.PluginId

/** Runtime integration contract for creating and releasing module-owned configuration scopes. */
interface ConfigurationRuntime : ConfigurationService, AutoCloseable {
    /** Returns the scope of [owner] registered under [pluginId] as module `main`, creating it on first use. */
    fun scope(owner: Any, pluginId: PluginId): ConfigScope

    /**
     * Returns the scope of [owner] kept under [key] for [module] of [plugin], creating it on first use.
     * The scope's files are listed as `plugin/module:path`.
     */
    fun scope(owner: Any, key: PluginId, plugin: PluginId, module: ModuleId): ConfigScope

    /** Closes every scope created for [owner]; call it when the owner is disabled. */
    fun close(owner: Any)
}
