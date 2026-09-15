package ru.privatenull.pnlibrary.api.plugin

/**
 * Optional display metadata overrides used during [PluginRegistry.register].
 *
 * Omitted values are discovered through the platform adapter. These methods do not change the
 * normalized [PluginId] used by registries and cross-plugin access rules.
 */
interface PluginMetadataBuilder {
    /** Overrides the human-readable plugin name. */
    fun name(value: String): PluginMetadataBuilder
    /** Overrides the plugin version shown in lifecycle and diagnostic output. */
    fun version(value: String): PluginMetadataBuilder
    /** Overrides the display-ready author text. */
    fun authors(value: String): PluginMetadataBuilder
}
