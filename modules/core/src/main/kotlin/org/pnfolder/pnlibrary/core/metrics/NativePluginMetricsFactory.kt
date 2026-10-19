package org.pnfolder.pnlibrary.core.metrics

import org.pnfolder.pnlibrary.api.metrics.PluginMetrics

/**
 * Metrics factory for platforms whose metrics owner is a native plugin object of type [P].
 *
 * The owner is validated once here; platforms only say how to open a bStats or FastStats session for a plugin.
 *
 * @param ownerType native plugin class every owner must be an instance of
 * @param platformName platform name used in the validation message, for example `Bukkit`
 * @param bStats opens a bStats session for a plugin and project identifier
 * @param fastStats opens a FastStats session for a plugin and project token
 */
open class NativePluginMetricsFactory<P : Any>(
    private val ownerType: Class<P>,
    private val platformName: String,
    private val bStats: (plugin: P, projectId: Int) -> PluginMetrics,
    private val fastStats: (plugin: P, token: String) -> PluginMetrics,
) : ProviderMetricsFactory() {

    /** @throws IllegalArgumentException when [owner] is not a native plugin of this platform */
    final override fun openBStats(owner: Any, projectId: Int): PluginMetrics = bStats(plugin(owner), projectId)

    /** @throws IllegalArgumentException when [owner] is not a native plugin of this platform */
    final override fun openFastStats(owner: Any, token: String): PluginMetrics = fastStats(plugin(owner), token)

    private fun plugin(owner: Any): P {
        require(ownerType.isInstance(owner)) {
            "$platformName metrics owner must be a $platformName plugin; received ${owner.javaClass.name}"
        }
        return ownerType.cast(owner)
    }
}
