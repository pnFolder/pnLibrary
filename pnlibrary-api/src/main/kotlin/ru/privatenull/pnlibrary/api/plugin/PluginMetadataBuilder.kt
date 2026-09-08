package ru.privatenull.pnlibrary.api.plugin

/** Optional overrides for metadata normally supplied by the native platform. */
interface PluginMetadataBuilder {
    fun name(value: String): PluginMetadataBuilder
    fun version(value: String): PluginMetadataBuilder
    fun authors(value: String): PluginMetadataBuilder
}
