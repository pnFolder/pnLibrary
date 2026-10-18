package ru.privatenull.pnlibrary.api.metrics

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
 * @property projectId positive bStats project identifier, required by [MetricsProvider.BSTATS]
 * @property token FastStats project token, required by [MetricsProvider.FASTSTATS]
 */
data class MetricsProviderConfiguration(
    val provider: MetricsProvider,
    val enabled: Boolean = true,
    val projectId: Int? = null,
    val token: String? = null,
) {
    /** Creates independently owned typed credentials for a provider session. */
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
        "MetricsProviderConfiguration(provider=$provider, enabled=$enabled, projectId=$projectId, token=[REDACTED])"
}
