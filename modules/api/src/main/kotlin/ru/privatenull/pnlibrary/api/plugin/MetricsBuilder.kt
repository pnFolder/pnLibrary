package ru.privatenull.pnlibrary.api.plugin

import ru.privatenull.pnlibrary.api.metrics.MetricsProvider
import ru.privatenull.pnlibrary.api.metrics.MetricsProviderConfiguration

/** Builds provider-neutral metrics configuration without exposing provider internals. */
class MetricsBuilder {
    private val configurations = mutableListOf<MetricsProviderConfiguration>()

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

    /** Returns the configured providers, rejecting an empty setup. */
    fun build(): List<MetricsProviderConfiguration> {
        require(configurations.isNotEmpty()) { "at least one metrics provider is required" }
        return configurations.toList()
    }

    private fun add(configuration: MetricsProviderConfiguration) {
        require(configurations.none { it.provider == configuration.provider }) {
            "metrics provider ${configuration.provider} is already configured"
        }
        configurations += configuration
    }
}
