package ru.privatenull.pnlibrary.bukkit

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import dev.faststats.Metrics
import dev.faststats.bukkit.BukkitContext
import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.api.metrics.MetricsCapability
import ru.privatenull.pnlibrary.api.metrics.MetricsProvider
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier
import ru.privatenull.pnlibrary.internal.faststats.FastStatsBridge

/** FastStats adapter. Charts are buffered until [start] so the context is built once. */
class FastStatsMetricsSession(
    private val plugin: Plugin,
    private val token: String,
) : PluginMetrics {
    /** Error reporter backed by the FastStats error tracker used by this session. */
    override val errorReporter = FastStatsErrorReporter()
    private val closed = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val pending = mutableListOf<(Metrics.Factory) -> Unit>()
    private var context: BukkitContext? = null

    /** Numeric project identifiers do not apply to token-based FastStats projects. */
    override val projectId: Int = 0

    /** Identifies FastStats as the backing provider. */
    override val provider: MetricsProvider = MetricsProvider.FASTSTATS

    /** Features implemented by the FastStats adapter. */
    override val capabilities: Set<MetricsCapability> = setOf(
        MetricsCapability.CHARTS,
        MetricsCapability.CUSTOM_VALUES,
        MetricsCapability.ERROR_TRACKING,
        MetricsCapability.CONTEXT_ATTRIBUTES,
    )

    /** Adds a string-valued pie metric named [id]. */
    override fun simplePie(id: String, value: Supplier<String?>): PluginMetrics = add {
        it.addMetric(FastStatsBridge.string(valid(id), Callable { value.get() ?: "unknown" }))
    }

    /** Adds a multi-valued pie metric named [id]. */
    override fun advancedPie(id: String, values: Supplier<Map<String, Int>>): PluginMetrics = add {
        it.addMetric(FastStatsBridge.numberMap(valid(id), Callable { values.get() }))
    }

    /** Adds a two-level drill-down pie metric named [id]. */
    override fun drilldownPie(id: String, values: Supplier<Map<String, Map<String, Int>>>): PluginMetrics = add {
        it.addMetric(FastStatsBridge.`object`(valid(id), Callable {
            JsonObject().also { root ->
                values.get().forEach { (group, entries) ->
                    root.add(group, JsonObject().also { child -> entries.forEach { (key, value) -> child.addProperty(key, value) } })
                }
            }
        }))
    }

    /** Adds a single integer line metric named [id]. */
    override fun singleLineChart(id: String, value: Supplier<Int>): PluginMetrics = add {
        it.addMetric(FastStatsBridge.number(valid(id), Callable { value.get() }))
    }

    /** Adds a multi-series line metric named [id]. */
    override fun multiLineChart(id: String, values: Supplier<Map<String, Int>>): PluginMetrics = advancedPie(id, values)

    /** Adds a category-based bar metric named [id]. */
    override fun simpleBarChart(id: String, values: Supplier<Map<String, Int>>): PluginMetrics = advancedPie(id, values)

    /** Adds a bar metric whose categories may contain several values. */
    override fun advancedBarChart(id: String, values: Supplier<Map<String, IntArray>>): PluginMetrics = add {
        it.addMetric(FastStatsBridge.`object`(valid(id), Callable {
            JsonObject().also { root ->
                values.get().forEach { (key, numbers) ->
                    root.add(key, JsonArray().also { array -> numbers.forEach(array::add) })
                }
            }
        }))
    }

    /** Creates and marks the buffered FastStats context as ready. */
    override fun start() {
        if (!started.compareAndSet(false, true) || closed.get()) return
        context = BukkitContext.Factory(plugin, token)
            .errorTrackerService(errorReporter.tracker)
            .metrics { factory ->
                pending.forEach { it(factory) }
                factory.create()
            }
            .create()
        context?.ready()
    }

    /** Shuts down the FastStats context once. */
    override fun close() {
        if (closed.compareAndSet(false, true)) context?.shutdown()
    }

    private fun add(register: (Metrics.Factory) -> Unit): PluginMetrics {
        check(!closed.get()) { "Metrics session is closed" }
        check(!started.get()) { "FastStats metrics must be configured before the module starts" }
        pending += register
        return this
    }

    private fun valid(id: String): String = id.trim().also {
        require(it.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid FastStats metric id: $id" }
    }

}
