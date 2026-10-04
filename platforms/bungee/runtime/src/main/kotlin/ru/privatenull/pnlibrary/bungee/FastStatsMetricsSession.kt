package ru.privatenull.pnlibrary.bungee

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dev.faststats.Metrics
import dev.faststats.bungee.BungeeContext
import net.md_5.bungee.api.plugin.Plugin
import ru.privatenull.pnlibrary.api.metrics.MetricsCapability
import ru.privatenull.pnlibrary.api.metrics.MetricsProvider
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier
import ru.privatenull.pnlibrary.internal.faststats.FastStatsBridge

/** FastStats adapter for BungeeCord and Waterfall. */
class FastStatsMetricsSession(private val plugin: Plugin, private val token: String) : PluginMetrics {
    override val errorReporter = FastStatsErrorReporter()
    private val closed = AtomicBoolean(false)
    private val started = AtomicBoolean(false)
    private val pending = mutableListOf<(Metrics.Factory) -> Unit>()
    private var context: BungeeContext? = null

    override val projectId = 0
    override val provider = MetricsProvider.FASTSTATS
    override val capabilities = setOf(MetricsCapability.CHARTS, MetricsCapability.CUSTOM_VALUES, MetricsCapability.ERROR_TRACKING, MetricsCapability.CONTEXT_ATTRIBUTES)

    override fun simplePie(id: String, value: Supplier<String?>) = add { it.addMetric(FastStatsBridge.string(valid(id), Callable { value.get() ?: "unknown" })) }
    override fun advancedPie(id: String, values: Supplier<Map<String, Int>>) = add { it.addMetric(FastStatsBridge.numberMap(valid(id), Callable { values.get() })) }
    override fun drilldownPie(id: String, values: Supplier<Map<String, Map<String, Int>>>) = add {
        it.addMetric(FastStatsBridge.`object`(valid(id), Callable {
            JsonObject().also { root -> values.get().forEach { (group, entries) -> root.add(group, JsonObject().also { child -> entries.forEach { (key, value) -> child.addProperty(key, value) } }) } }
        }))
    }
    override fun singleLineChart(id: String, value: Supplier<Int>) = add { it.addMetric(FastStatsBridge.number(valid(id), Callable { value.get() })) }
    override fun multiLineChart(id: String, values: Supplier<Map<String, Int>>) = advancedPie(id, values)
    override fun simpleBarChart(id: String, values: Supplier<Map<String, Int>>) = advancedPie(id, values)
    override fun advancedBarChart(id: String, values: Supplier<Map<String, IntArray>>) = add {
        it.addMetric(FastStatsBridge.`object`(valid(id), Callable { JsonObject().also { root -> values.get().forEach { (key, numbers) -> root.add(key, JsonArray().also { array -> numbers.forEach(array::add) }) } } }))
    }

    override fun start() {
        if (!started.compareAndSet(false, true) || closed.get()) return
        context = BungeeContext.Factory(plugin, token)
            .errorTrackerService(errorReporter.tracker)
            .metrics { factory ->
                pending.forEach { register -> register(factory) }
                factory.create()
            }
            .create()
        context?.ready()
    }
    override fun close() { if (closed.compareAndSet(false, true)) context?.shutdown() }

    private fun add(register: (Metrics.Factory) -> Unit): PluginMetrics {
        check(!closed.get()) { "Metrics session is closed" }
        check(!started.get()) { "FastStats metrics must be configured before the module starts" }
        pending += register
        return this
    }
    private fun valid(id: String): String = id.trim().also { require(it.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid FastStats metric id: $id" } }
}
