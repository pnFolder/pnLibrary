package org.pnfolder.pnlibrary.spi.platform

/** Installed native plugin inventory exposed to dependency and update services. */
interface PlatformPluginInventory {
    /** Installed plugin names mapped to their versions. */
    fun installedPlugins(): Map<String, String> = emptyMap()
}
