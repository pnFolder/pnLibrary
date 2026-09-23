package ru.privatenull.pnlibrary.api.plugin

/** Optional switches that change how pnLibrary integrates one registered plugin. */
interface PluginOptions {
    /**
     * Controls publication of eligible placeholders through the detected PlaceholderAPI adapter.
     *
     * This option is enabled by default. Disabling it does not disable pnLibrary's own placeholder
 * resolution through [ModuleContext.placeholders].
     */
    fun placeholderApi(enabled: Boolean): PluginOptions
}
