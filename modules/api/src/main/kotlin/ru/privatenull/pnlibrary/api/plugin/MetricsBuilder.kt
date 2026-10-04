package ru.privatenull.pnlibrary.api.plugin

import ru.privatenull.pnlibrary.api.metrics.MetricsProvider
import ru.privatenull.pnlibrary.api.metrics.MetricsProviderConfiguration
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import java.util.function.Consumer

/** Builds provider-neutral metrics configuration without exposing provider internals. */
class MetricsBuilder {
    private val configurations = mutableListOf<MetricsProviderConfiguration>()
    private val configurers = mutableListOf<Consumer<PluginMetrics>>()

    /** Whether metrics start together with the module. */
    var enabled: Boolean = true
        private set

    /** Selects the initial state without leaving the metrics DSL. */
    fun enabled(value: Boolean): MetricsBuilder = apply {
        enabled = value
    }

    /** Enables bStats for this module. */
    fun bStats(projectId: Int): MetricsBuilder = apply {
        require(projectId > 0) { "metrics projectId must be positive" }
        add(MetricsProviderConfiguration(MetricsProvider.BSTATS, projectId = projectId))
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
        require(configurations.isNotEmpty()) { "at least one metrics provider is required" }
        return configurations.toList()
    }

    internal fun configure(metrics: PluginMetrics) {
        configurers.forEach { it.accept(metrics) }
    }

    private fun add(configuration: MetricsProviderConfiguration) {
        require(configurations.none { it.provider == configuration.provider }) {
            "metrics provider ${configuration.provider} is already configured"
        }
        configurations += configuration
    }
}
