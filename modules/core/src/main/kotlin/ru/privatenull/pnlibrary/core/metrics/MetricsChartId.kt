package ru.privatenull.pnlibrary.core.metrics

/** Chart identifier rules accepted by every supported metrics provider. */
internal object MetricsChartId {
    private val PATTERN = Regex("[A-Za-z0-9_-]{1,64}")

    /** Trims [id] and rejects values that a provider could not represent. */
    @JvmStatic
    fun normalize(id: String): String = id.trim().also {
        require(PATTERN.matches(it)) { "Invalid metrics chart id: $id" }
    }
}
