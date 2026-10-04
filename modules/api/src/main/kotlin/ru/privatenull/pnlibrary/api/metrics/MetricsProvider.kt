package ru.privatenull.pnlibrary.api.metrics

/** Identifies a metrics transport without exposing its SDK classes to consumers. */
enum class MetricsProvider {
    BSTATS,
    FASTSTATS,
}

/** Features that a metrics provider may expose beyond the shared chart API. */
enum class MetricsCapability {
    CHARTS,
    CUSTOM_VALUES,
    ERROR_TRACKING,
    CONTEXT_ATTRIBUTES,
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
)
