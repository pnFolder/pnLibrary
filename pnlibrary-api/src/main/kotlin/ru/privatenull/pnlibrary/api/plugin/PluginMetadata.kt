package ru.privatenull.pnlibrary.api.plugin

import ru.privatenull.pnlibrary.api.platform.PlatformType

/** Runtime metadata resolved automatically from the native plugin and platform. */
class PluginMetadata(
    val id: PluginId,
    val name: String,
    val version: String,
    val authors: String,
    val platform: PlatformType,
    val platformImplementation: String,
    val javaVersion: String,
    val javaFeature: Int,
)
