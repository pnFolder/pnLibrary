package org.pnfolder.pnlibrary.api.metrics

import org.pnfolder.pnlibrary.api.platform.PlatformType
/** Identifies a metrics transport without exposing its SDK classes to consumers. */
enum class MetricsProvider {
    BSTATS,
    FASTSTATS,
}

/**
 * Provider-neutral credentials and enablement requested for one plugin module.
 *
 * @property provider metrics backend to open
 * @property enabled whether this backend participates in the resulting metrics session
 * @property projectId positive bStats project identifier used on every platform without its own entry in [platformProjectIds]
 * @property platformProjectIds bStats project identifiers by platform: bStats keeps one project per platform
 * @property token FastStats project token, required by [MetricsProvider.FASTSTATS]
 */
data class MetricsProviderConfiguration(
    val provider: MetricsProvider,
    val enabled: Boolean = true,
    val projectId: Int? = null,
    val platformProjectIds: Map<PlatformType, Int> = emptyMap(),
    val token: String? = null,
) {
    /**
     * Returns this configuration with the bStats project of [platform]: its own entry first, then [projectId].
     * Throws [IllegalArgumentException] when bStats has no project for the platform.
     */
    fun forPlatform(platform: PlatformType): MetricsProviderConfiguration {
        if (provider != MetricsProvider.BSTATS) return this
        val id = platformProjectIds[platform] ?: projectId
        requireNotNull(id) { "No bStats project ID is configured for platform ${platform.displayName}" }
        return copy(projectId = id, platformProjectIds = emptyMap())
    }

    /** Returns the provider credentials built from this configuration. */
    fun credentials(): MetricsCredentials<*> = when (provider) {
        MetricsProvider.BSTATS -> MetricsCredentials.BStats(
            requireNotNull(projectId) { "Metrics project ID is required for bStats" },
        )
        MetricsProvider.FASTSTATS -> {
            val characters = requireNotNull(token) { "FastStats requires a project token" }.toCharArray()
            try {
                MetricsCredentials.FastStats(characters)
            } finally {
                characters.fill('\u0000')
            }
        }
    }

    /** Keeps legacy configuration logs from exposing project tokens. */
    override fun toString(): String =
        "MetricsProviderConfiguration(provider=$provider, enabled=$enabled, projectId=$projectId, platformProjectIds=$platformProjectIds, token=[REDACTED])"
}
