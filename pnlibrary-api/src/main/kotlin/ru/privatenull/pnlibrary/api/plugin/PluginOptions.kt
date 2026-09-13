package ru.privatenull.pnlibrary.api.plugin

/** Optional switches that change how pnLibrary integrates one registered plugin. */
interface PluginOptions {
    /** Publishes placeholders explicitly marked for PlaceholderAPI. Enabled by default. */
    fun placeholderApi(enabled: Boolean): PluginOptions
}
