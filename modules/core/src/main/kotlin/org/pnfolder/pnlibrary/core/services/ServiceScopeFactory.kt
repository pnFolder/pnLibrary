package org.pnfolder.pnlibrary.core.services

import org.pnfolder.pnlibrary.api.plugin.PluginId

/** Creates service views without exposing the registry's registration implementation. */
internal fun interface ServiceScopeFactory {
    fun ownedBy(owner: PluginId): ServiceScope
}
