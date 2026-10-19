package org.pnfolder.pnlibrary.core.plugin

import org.pnfolder.pnlibrary.api.metrics.MetricsProviderConfiguration
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics
import java.util.function.Consumer

/** Complete metrics declaration owned by one registered module. */
internal data class ModuleMetricsDefinition(
    val providers: List<MetricsProviderConfiguration> = emptyList(),
    val enabled: Boolean = false,
    val configurers: List<Consumer<PluginMetrics>> = emptyList(),
) {
    init {
        require(providers.size <= 2) { "At most two metrics providers are supported" }
        require(providers.map { it.provider }.distinct().size == providers.size) {
            "Each metrics provider may be configured only once; combine bStats and FastStats in one metrics block"
        }
    }
}
