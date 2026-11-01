package org.pnfolder.pnlibrary.api.metrics

import org.pnfolder.pnlibrary.api.metrics.MetricsProvider
import org.pnfolder.pnlibrary.api.metrics.MetricsProviderConfiguration
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics
import org.pnfolder.pnlibrary.api.platform.PlatformType
import java.util.function.Consumer

/** Builds provider-neutral metrics configuration without exposing provider internals. */
class MetricsBuilder {
    private val configurations = mutableListOf<MetricsProviderConfiguration>()
    private val configurers = mutableListOf<Consumer<PluginMetrics>>()
    private var bStatsDefault: Int? = null
    private val bStatsByPlatform = linkedMapOf<PlatformType, Int>()

    /** Whether metrics start together with the module. */
    var enabled: Boolean = true
        private set

    /** Selects the initial state without leaving the metrics DSL. */
    fun enabled(value: Boolean): MetricsBuilder = apply {
        enabled = value
    }

    /** Enables bStats for this module with one project used on every platform without its own ID. */
    fun bStats(projectId: Int): MetricsBuilder = apply {
        require(projectId > 0) { "Metrics project ID must be positive" }
        check(bStatsDefault == null) { "Metrics provider ${MetricsProvider.BSTATS} is already configured" }
        bStatsDefault = projectId
    }

    /** Enables bStats for this module with the project of one platform; bStats keeps a separate project per platform. */
    fun bStats(platform: PlatformType, projectId: Int): MetricsBuilder = apply {
        require(projectId > 0) { "Metrics project ID must be positive" }
        check(platform !in bStatsByPlatform) { "bStats project for ${platform.displayName} is already configured" }
        bStatsByPlatform[platform] = projectId
    }

    /** Enables bStats for this module with the projects of several platforms at once. */
    fun bStats(projectIds: Map<PlatformType, Int>): MetricsBuilder = apply {
        projectIds.forEach { (platform, projectId) -> bStats(platform, projectId) }
    }

    /** Enables FastStats for this module. */
    fun fastStats(token: String): MetricsBuilder = apply {
        require(token.isNotBlank()) { "FastStats token must not be blank" }
        add(MetricsProviderConfiguration(MetricsProvider.FASTSTATS, token = token))
    }

    /** Registers charts for every enabled provider. */
    fun charts(configure: Consumer<PluginMetrics>): MetricsBuilder = apply {
        configurers += configure
    }

    /** Returns the configured providers, rejecting an empty setup. */
    fun build(): List<MetricsProviderConfiguration> {
        val bStats = if (bStatsDefault != null || bStatsByPlatform.isNotEmpty()) {
            listOf(
                MetricsProviderConfiguration(
                    MetricsProvider.BSTATS,
                    projectId = bStatsDefault,
                    platformProjectIds = bStatsByPlatform.toMap(),
                ),
            )
        } else {
            emptyList()
        }
        require(configurations.isNotEmpty() || bStats.isNotEmpty()) { "At least one metrics provider is required" }
        return bStats + configurations
    }

    /** Replays registered chart callbacks when a runtime session is created or restarted. */
    fun configure(metrics: PluginMetrics) {
        configurers.forEach { it.accept(metrics) }
    }

    private fun add(configuration: MetricsProviderConfiguration) {
        require(configurations.none { it.provider == configuration.provider }) {
            "Metrics provider ${configuration.provider} is already configured"
        }
        configurations += configuration
    }
}
