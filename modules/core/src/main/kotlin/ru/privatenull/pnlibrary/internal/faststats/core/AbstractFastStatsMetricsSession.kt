package ru.privatenull.pnlibrary.internal.faststats.core

import dev.faststats.ErrorTracker
import dev.faststats.Metrics
import ru.privatenull.pnlibrary.api.metrics.MetricsProvider
import ru.privatenull.pnlibrary.api.metrics.MetricsCredentials
import ru.privatenull.pnlibrary.core.metrics.AbstractMetricsSession
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Translates shared charts to FastStats metrics and owns the platform context.
 *
 * Configure charts before calling [start]. The context starts submission only after the
 * configured metrics have been registered; error reporting remains available through this session.
 */
abstract class AbstractFastStatsMetricsSession(
    private val fastStatsCredentials: MetricsCredentials.FastStats,
    private val contextFactory: FastStatsContextFactory,
) : AbstractMetricsSession(fastStatsCredentials) {

    /** Accepts the legacy String API without retaining the String in this session. */
    constructor(token: String, contextFactory: FastStatsContextFactory) :
        this(fastStatsCredentials(token), contextFactory)

    /** Copies caller-owned token characters; the caller remains responsible for erasing its array. */
    constructor(token: CharArray, contextFactory: FastStatsContextFactory) :
        this(MetricsCredentials.FastStats(token), contextFactory)

    override val errorReporter = FastStatsErrorReporter()
    override val provider: MetricsProvider = MetricsProvider.FASTSTATS
    private val started = AtomicBoolean(false)
    private val pending = mutableListOf<Chart>()
    private var context: FastStatsContext? = null

    /** Retains a lazy chart until the native FastStats factory is initialized. */
    override fun register(chart: Chart) {
        check(!started.get()) { "FastStats metrics must be configured before the session starts" }
        pending += chart
    }

    /** Initializes and starts native submission after chart configuration. */
    override fun start() {
        if (isClosed || !started.compareAndSet(false, true)) {
            return
        }
        context = fastStatsCredentials.useToken { token ->
            contextFactory.create(token, errorReporter.tracker) { factory ->
                pending.forEach { chart -> registerNative(factory, chart) }
                factory.create()
            }
        }
        context?.ready()
    }

    /** Releases the platform-specific FastStats context. */
    override fun shutdown() {
        context?.shutdown()
        pending.clear()
    }

    /** Maps chart shapes to the metric types supported by FastStats. */
    private fun registerNative(factory: Metrics.Factory, chart: Chart) {
        when (chart) {
            is Chart.SimplePie -> FastStatsSupport.simplePie(factory, chart.id, chart.value)
            is Chart.AdvancedPie -> FastStatsSupport.advancedPie(factory, chart.id, chart.values)
            is Chart.DrilldownPie -> FastStatsSupport.drilldownPie(factory, chart.id, chart.values)
            is Chart.SingleLine -> FastStatsSupport.singleLineChart(factory, chart.id, chart.value)
            is Chart.MultiLine -> FastStatsSupport.advancedPie(factory, chart.id, chart.values)
            is Chart.SimpleBar -> FastStatsSupport.advancedPie(factory, chart.id, chart.values)
            is Chart.AdvancedBar -> FastStatsSupport.advancedBarChart(factory, chart.id, chart.values)
        }
    }
}

/** Copies a legacy String token into owned credentials and erases the intermediate buffer. */
private fun fastStatsCredentials(token: String): MetricsCredentials.FastStats {
    val characters = token.toCharArray()
    return try {
        MetricsCredentials.FastStats(characters)
    } finally {
        characters.fill('\u0000')
    }
}

/** Creates the platform-specific FastStats context without leaking SDK types into the API. */
fun interface FastStatsContextFactory {

    /** Builds the native context with its error tracker and lazy metrics factory. */
    fun create(token: String, tracker: ErrorTracker, metrics: (Metrics.Factory) -> Metrics): FastStatsContext
}

/** Minimal lifecycle contract shared by all FastStats platform contexts. */
interface FastStatsContext {

    /** Starts submission after initialization. */
    fun ready()

    /** Releases native scheduling and submission resources. */
    fun shutdown()
}
