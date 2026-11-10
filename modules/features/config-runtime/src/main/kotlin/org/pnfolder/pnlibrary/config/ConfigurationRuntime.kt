package org.pnfolder.pnlibrary.config

import org.pnfolder.pnlibrary.api.config.ConfigScope
import org.pnfolder.pnlibrary.api.config.ConfigurationService
import org.pnfolder.pnlibrary.api.plugin.PluginId

/** Runtime integration contract for creating and releasing module-owned configuration scopes. */
interface ConfigurationRuntime : ConfigurationService, AutoCloseable {
    /** Returns the scope of [owner] registered under [pluginId], creating it on first use. */
    fun scope(owner: Any, pluginId: PluginId): ConfigScope

    /** Closes every scope created for [owner]; call it when the owner is disabled. */
    fun close(owner: Any)
}
