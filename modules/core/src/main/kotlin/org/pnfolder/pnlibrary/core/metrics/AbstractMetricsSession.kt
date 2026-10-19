package org.pnfolder.pnlibrary.core.metrics

import org.pnfolder.pnlibrary.api.metrics.MetricsCredentials
import org.pnfolder.pnlibrary.api.metrics.MetricsProvider
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier

/**
 * Provides fluent chart registration and lifecycle checks shared by metrics providers.
 *
 * Suppliers are retained without evaluation. Providers translate [Chart] into their native
 * format and invoke suppliers only when collecting samples.
 *
 * @property credentials credentials of the provider this session reports to
 */
abstract class AbstractMetricsSession(
    val credentials: MetricsCredentials<*>,
) : PluginMetrics {

    /** The single provider this session reports to, taken from [credentials]. */
    final override val providers: Set<MetricsProvider> = setOf(credentials.provider)

    private val closed = AtomicBoolean(false)

    /** Whether this session has already released its provider resources. */
    protected val isClosed: Boolean
        get() = closed.get()

    /** Registers a categorical sample without evaluating its supplier. */
    final override fun simplePie(id: String, value: Supplier<String?>): PluginMetrics =
        add(Chart.SimplePie(normalizeId(id), value))

    /** Registers category counts. */
    final override fun advancedPie(id: String, values: Supplier<Map<String, Int>>): PluginMetrics =
        add(Chart.AdvancedPie(normalizeId(id), values))

    /** Registers nested category counts. */
    final override fun drilldownPie(id: String, values: Supplier<Map<String, Map<String, Int>>>): PluginMetrics =
        add(Chart.DrilldownPie(normalizeId(id), values))

    /** Registers one numeric time-series sample. */
    final override fun singleLineChart(id: String, value: Supplier<Int>): PluginMetrics =
        add(Chart.SingleLine(normalizeId(id), value))

    /** Registers named numeric time-series samples. */
    final override fun multiLineChart(id: String, values: Supplier<Map<String, Int>>): PluginMetrics =
        add(Chart.MultiLine(normalizeId(id), values))

    /** Registers named bars. */
    final override fun simpleBarChart(id: String, values: Supplier<Map<String, Int>>): PluginMetrics =
        add(Chart.SimpleBar(normalizeId(id), values))

    /** Registers named arrays of bars. */
    final override fun advancedBarChart(id: String, values: Supplier<Map<String, IntArray>>): PluginMetrics =
        add(Chart.AdvancedBar(normalizeId(id), values))

    /** Releases provider resources at most once and prevents further registration. */
    final override fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                shutdown()
            } finally {
                credentials.close()
            }
        }
    }

    /** Translates a validated chart into the provider's native registration format. */
    protected abstract fun register(chart: Chart)

    /** Releases resources owned by the provider. Called only once. */
    protected abstract fun shutdown()

    /** Checks lifecycle state before handing registration to the provider. */
    private fun add(chart: Chart): PluginMetrics {
        check(!isClosed) { "Metrics session is closed" }
        register(chart)
        return this
    }

    /** Trims and validates the identifier accepted by both supported providers. */
    private fun normalizeId(id: String): String {
        val normalized = id.trim()
        require(CHART_ID.matches(normalized)) { "Invalid metrics chart id: $id" }
        return normalized
    }

    /**
     * Typed, lazy chart definitions shared by provider adapters.
     *
     * @property id stable chart identifier used by the provider
     */
    sealed class Chart(open val id: String) {

        /**
         * A single categorical string sample.
         *
         * @property id stable chart identifier used by the provider
         * @property value supplier of the current category, or `null` when there is none
         */
        data class SimplePie(override val id: String, val value: Supplier<String?>) : Chart(id)

        /**
         * Category-to-count samples.
         *
         * @property id stable chart identifier used by the provider
         * @property values supplier of category-to-count pairs
         */
        data class AdvancedPie(override val id: String, val values: Supplier<Map<String, Int>>) : Chart(id)

        /**
         * Group-to-category-to-count samples.
         *
         * @property id stable chart identifier used by the provider
         * @property values supplier of group-to-category-to-count pairs
         */
        data class DrilldownPie(override val id: String, val values: Supplier<Map<String, Map<String, Int>>>) : Chart(id)

        /**
         * A single numeric time-series sample.
         *
         * @property id stable chart identifier used by the provider
         * @property value supplier of the current number
         */
        data class SingleLine(override val id: String, val value: Supplier<Int>) : Chart(id)

        /**
         * Named numeric time-series samples.
         *
         * @property id stable chart identifier used by the provider
         * @property values supplier of series name-to-number pairs
         */
        data class MultiLine(override val id: String, val values: Supplier<Map<String, Int>>) : Chart(id)

        /**
         * Named numeric bars.
         *
         * @property id stable chart identifier used by the provider
         * @property values supplier of bar name-to-number pairs
         */
        data class SimpleBar(override val id: String, val values: Supplier<Map<String, Int>>) : Chart(id)

        /**
         * Named numeric arrays of bars.
         *
         * @property id stable chart identifier used by the provider
         * @property values supplier of bar name-to-values pairs
         */
        data class AdvancedBar(override val id: String, val values: Supplier<Map<String, IntArray>>) : Chart(id)
    }

    private companion object {
        val CHART_ID = Regex("[A-Za-z0-9_-]{1,64}")
    }
}
