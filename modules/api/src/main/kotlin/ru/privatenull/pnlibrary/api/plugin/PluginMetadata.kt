package ru.privatenull.pnlibrary.api.plugin

import ru.privatenull.pnlibrary.api.platform.PlatformType

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
)
