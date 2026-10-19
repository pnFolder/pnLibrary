package org.pnfolder.pnlibrary.spi.platform

/** Installed native plugin inventory exposed to dependency and update services. */
interface PlatformPluginInventory {
    fun installedPlugins(): Map<String, String> = emptyMap()
}
