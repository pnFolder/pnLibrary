package ru.privatenull.pnlibrary.internal.faststats.core

import dev.faststats.ErrorTracker
import dev.faststats.Metrics
import ru.privatenull.pnlibrary.api.metrics.MetricsCapability
import ru.privatenull.pnlibrary.api.metrics.MetricsProvider
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier

/** A platform-neutral FastStats session with the complete metrics lifecycle. */
abstract class AbstractFastStatsMetricsSession(
    private val token: String,
    private val contextFactory: FastStatsContextFactory,
) : PluginMetrics {
    override val errorReporter = FastStatsErrorReporter()
    override val projectId: Int = 0
    override val provider: MetricsProvider = MetricsProvider.FASTSTATS
    override val capabilities: Set<MetricsCapability> = setOf(
        MetricsCapability.CHARTS,
        MetricsCapability.CUSTOM_VALUES,
        MetricsCapability.ERROR_TRACKING,
        MetricsCapability.CONTEXT_ATTRIBUTES,
    )

    private val closed = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val pending = mutableListOf<(Metrics.Factory) -> Unit>()
    private var context: FastStatsContext? = null

    override fun simplePie(id: String, value: Supplier<String?>): PluginMetrics = add {
        FastStatsSupport.simplePie(it, id, value)
    }

    override fun advancedPie(id: String, values: Supplier<Map<String, Int>>): PluginMetrics = add {
        FastStatsSupport.advancedPie(it, id, values)
    }

    override fun drilldownPie(id: String, values: Supplier<Map<String, Map<String, Int>>>): PluginMetrics = add {
        FastStatsSupport.drilldownPie(it, id, values)
    }

    override fun singleLineChart(id: String, value: Supplier<Int>): PluginMetrics = add {
        FastStatsSupport.singleLineChart(it, id, value)
    }

    override fun multiLineChart(id: String, values: Supplier<Map<String, Int>>): PluginMetrics = advancedPie(id, values)

    override fun simpleBarChart(id: String, values: Supplier<Map<String, Int>>): PluginMetrics = advancedPie(id, values)

    override fun advancedBarChart(id: String, values: Supplier<Map<String, IntArray>>): PluginMetrics = add {
        FastStatsSupport.advancedBarChart(it, id, values)
    }

    override fun start() {
        if (!started.compareAndSet(false, true) || closed.get()) return
        context = contextFactory.create(token, errorReporter.tracker) { factory ->
            pending.forEach { register -> register(factory) }
            factory.create()
        }
        context?.ready()
    }

    override fun close() {
        if (closed.compareAndSet(false, true)) context?.shutdown()
    }

    private fun add(register: (Metrics.Factory) -> Unit): PluginMetrics {
        check(!closed.get()) { "Metrics session is closed" }
        check(!started.get()) { "FastStats metrics must be configured before the session starts" }
        pending += register
        return this
    }
}

/** Creates the platform-specific FastStats context without leaking SDK types into the API. */
fun interface FastStatsContextFactory {
    fun create(token: String, tracker: ErrorTracker, metrics: (Metrics.Factory) -> Metrics): FastStatsContext
}

/** Minimal lifecycle contract shared by all FastStats platform contexts. */
interface FastStatsContext {
    fun ready()
    fun shutdown()
}
