package org.pnfolder.pnlibrary.config

import org.pnfolder.pnlibrary.api.config.ConfigScope
import org.pnfolder.pnlibrary.api.config.ConfigurationService
import org.pnfolder.pnlibrary.api.plugin.PluginId

/** Runtime integration contract for creating and releasing module-owned configuration scopes. */
interface ConfigurationRuntime : ConfigurationService, AutoCloseable {
    fun scope(owner: Any, pluginId: PluginId): ConfigScope

    fun close(owner: Any)
}
