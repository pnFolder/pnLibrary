package org.pnfolder.pnlibrary.api.plugin.metadata

import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.plugin.ModuleId

/**
 * Immutable runtime metadata resolved when a plugin context is registered.
 *
 * @property id normalized pnLibrary plugin identity
 * @property name display name reported by the platform or overridden by [PluginMetadataBuilder]
 * @property version plugin version string, or `unknown` when unavailable
 * @property authors display-ready author list, or `unknown` when unavailable
 * @property platform normalized server platform family
 * @property platformImplementation concrete platform implementation and version description
 * @property javaVersion complete JVM version string from the running process
 * @property javaFeature JVM feature release, for example `21`
 * @property description one or two sentences on what the plugin or module is for, read by administrators in the menu; `null` when not given
 */
class PluginMetadata(
    val id: ModuleId,
    val name: String,
    val version: String,
    val authors: String,
    val platform: PlatformType,
    val platformImplementation: String,
    val javaVersion: String,
    val javaFeature: Int,
    val description: String? = null,
)
