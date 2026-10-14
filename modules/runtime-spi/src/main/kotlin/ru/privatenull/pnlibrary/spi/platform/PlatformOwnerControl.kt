package ru.privatenull.pnlibrary.spi.platform

import ru.privatenull.pnlibrary.api.plugin.PluginMetadata
import ru.privatenull.pnlibrary.api.remote.RemotePolicyContext

/** Native owner control operations used by remote policy enforcement. */
interface PlatformOwnerControl {
    fun remotePolicyContext(owner: Any, metadata: PluginMetadata, values: Map<String, String>): RemotePolicyContext =
        throw UnsupportedOperationException("Remote policy is unavailable")

    fun disableOwner(owner: Any): Boolean = false
}
