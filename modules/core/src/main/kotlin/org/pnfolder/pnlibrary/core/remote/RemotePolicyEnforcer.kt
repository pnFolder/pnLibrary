package org.pnfolder.pnlibrary.core.remote

import org.pnfolder.pnlibrary.api.logging.PnLogger
import org.pnfolder.pnlibrary.api.plugin.PluginMetadata
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings
import org.pnfolder.pnlibrary.api.tasks.TaskScope

/** Contract the plugin registry uses to apply a module's remote policy. */
internal interface RemotePolicyEnforcer {
    fun start(
        owner: Any,
        metadata: PluginMetadata,
        policy: RemotePolicySettings,
        tasks: TaskScope,
        isClosed: () -> Boolean,
        logger: PnLogger,
        closeModule: () -> Unit,
        closePlugin: () -> Unit,
    )
}
