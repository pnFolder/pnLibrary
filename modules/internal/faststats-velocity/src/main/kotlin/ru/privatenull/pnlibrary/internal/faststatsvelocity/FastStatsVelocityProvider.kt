package ru.privatenull.pnlibrary.internal.faststatsvelocity

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.velocitypowered.api.plugin.PluginContainer
import com.velocitypowered.api.proxy.ProxyServer
import dev.faststats.Metrics
import dev.faststats.TrackedError
import dev.faststats.velocity.VelocityContext
import org.slf4j.Logger
import ru.privatenull.pnlibrary.api.metrics.ErrorReporter
import ru.privatenull.pnlibrary.api.metrics.MetricsCapability
import ru.privatenull.pnlibrary.api.metrics.MetricsProvider
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.api.metrics.TelemetryError
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Supplier

/** Creates a Velocity FastStats session with charts and its own error tracker. */
class FastStatsVelocityProvider {
    fun open(
        owner: Any,
        server: ProxyServer,
        logger: Logger,
        dataDirectory: Path,
        token: String,
    ): PluginMetrics = Session(owner, server, logger, dataDirectory, token)

    private class Session(
        owner: Any,
        private val server: ProxyServer,
        private val logger: Logger,
        private val dataDirectory: Path,
        private val token: String,
    ) : PluginMetrics {
        override val errorReporter = FastStatsErrorReporter()
        private val closed = AtomicBoolean(false)
        private val started = AtomicBoolean(false)
        private val pending = mutableListOf<(Metrics.Factory) -> Unit>()
        private var context: VelocityContext? = null
        private val container: PluginContainer = server.pluginManager.fromInstance(owner).orElseThrow()
        override val projectId = 0
        override val provider = MetricsProvider.FASTSTATS
        override val capabilities = setOf(MetricsCapability.CHARTS, MetricsCapability.CUSTOM_VALUES, MetricsCapability.ERROR_TRACKING, MetricsCapability.CONTEXT_ATTRIBUTES)
        private fun add(action: (Metrics.Factory) -> Unit): PluginMetrics {
            check(!closed.get()) { "Metrics session is closed" }
            check(!started.get()) { "Configure charts before starting the session" }
            pending += action
            return this
        }
        private fun id(id: String) = id.trim().also { require(it.matches(Regex("[A-Za-z0-9_-]{1,64}"))) }
        override fun simplePie(i: String, v: Supplier<String?>) = add { it.addMetric(FastStatsBridge.string(id(i), Callable { v.get() ?: "unknown" })) }
        override fun advancedPie(i: String, v: Supplier<Map<String, Int>>) = add { it.addMetric(FastStatsBridge.numberMap(id(i), Callable { v.get() })) }
        override fun drilldownPie(i: String, v: Supplier<Map<String, Map<String, Int>>>) = add { it.addMetric(FastStatsBridge.`object`(id(i), Callable { JsonObject().also { r -> v.get().forEach { (g, e) -> r.add(g, JsonObject().also { c -> e.forEach { (k, n) -> c.addProperty(k, n) } }) } } })) }
        override fun singleLineChart(i: String, v: Supplier<Int>) = add { it.addMetric(FastStatsBridge.number(id(i), Callable { v.get() })) }
        override fun multiLineChart(i: String, v: Supplier<Map<String, Int>>) = advancedPie(i, v)
        override fun simpleBarChart(i: String, v: Supplier<Map<String, Int>>) = advancedPie(i, v)
        override fun advancedBarChart(i: String, v: Supplier<Map<String, IntArray>>) = add { it.addMetric(FastStatsBridge.`object`(id(i), Callable { JsonObject().also { r -> v.get().forEach { (k, a) -> r.add(k, JsonArray().also { j -> a.forEach(j::add) }) } } })) }
        override fun start() {
            if (!started.compareAndSet(false, true) || closed.get()) return
            context = VelocityContext.Factory(container, server, logger, dataDirectory)
                .token(token)
                .errorTrackerService(errorReporter.tracker)
                .metrics { factory ->
                    pending.forEach { register -> register(factory) }
                    factory.create()
                }
                .create()
            context?.ready()
        }
        override fun close() {
            if (closed.compareAndSet(false, true)) context?.shutdown()
        }
    }

    private class FastStatsErrorReporter : ErrorReporter {
        val tracker = FastStatsBridge.errorTracker()
        override fun capture(error: TelemetryError) {
            val throwable = RuntimeException("${error.type}: ${error.message}").apply {
                stackTrace = error.stackTrace.map { frame ->
                    StackTraceElement("reported", frame, null, -1)
                }.toTypedArray()
            }
            val tracked: TrackedError = tracker.trackError(
                throwable,
            )
            tracked.handled(error.handled)
            error.operation?.let { tracked.attributes().put("operation", it) }
            error.attributes.forEach { (key, value) ->
                value?.let { tracked.attributes().put(key, it.toString()) }
            }
        }
        override fun close() = Unit
    }
}
