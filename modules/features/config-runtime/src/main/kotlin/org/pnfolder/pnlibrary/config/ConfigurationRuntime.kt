package org.pnfolder.pnlibrary.config

import org.pnfolder.pnlibrary.api.config.ConfigScope
import org.pnfolder.pnlibrary.api.config.ConfigurationService
import org.pnfolder.pnlibrary.api.plugin.PluginId

/** Runtime integration contract for creating and releasing module-owned configuration scopes. */
interface ConfigurationRuntime : ConfigurationService, AutoCloseable {
    /** Returns the configuration scope identified by the native [owner] and logical [pluginId]. */
    fun scope(owner: Any, pluginId: PluginId): ConfigScope

    /** Releases all configuration scopes associated with the native [owner]. */
    fun close(owner: Any)
}
