package ru.privatenull.pnlibrary.core.plugin

import ru.privatenull.pnlibrary.api.metrics.MetricsProviderConfiguration
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import java.util.function.Consumer

/** Complete metrics declaration owned by one registered module. */
internal data class ModuleMetricsDefinition(
    val providers: List<MetricsProviderConfiguration> = emptyList(),
    val enabled: Boolean = false,
    val configurers: List<Consumer<PluginMetrics>> = emptyList(),
) {
    init {
        require(providers.size <= 2) { "at most two metrics providers are supported" }
        require(providers.map { it.provider }.distinct().size == providers.size) {
            "metrics providers must be unique"
        }
    }

    val projectId: Int?
        get() = providers.firstNotNullOfOrNull { it.projectId }
}
