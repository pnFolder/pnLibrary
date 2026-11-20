package org.pnfolder.pnlibrary.core.metrics.faststats

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dev.faststats.Metrics
import dev.faststats.TrackedError
import org.pnfolder.pnlibrary.api.metrics.error.ErrorReporter
import org.pnfolder.pnlibrary.api.metrics.error.TelemetryError
import org.pnfolder.pnlibrary.core.metrics.validChartId
import org.pnfolder.pnlibrary.internal.faststats.FastStatsBridge
import java.util.concurrent.Callable
import java.util.function.Supplier

/** Shared, platform-neutral FastStats helpers used by every platform adapter. */
internal object FastStatsSupport {
    fun simplePie(factory: Metrics.Factory, id: String, value: Supplier<String?>) =
        factory.addMetric(FastStatsBridge.string(id.validChartId(), Callable { value.get() ?: "unknown" }))

    /** Publishes name-to-count pairs; FastStats represents pies, lines and simple bars identically. */
    fun numberMap(factory: Metrics.Factory, id: String, values: Supplier<Map<String, Int>>) =
        factory.addMetric(FastStatsBridge.numberMap(id.validChartId(), Callable { values.get() }))

    fun drilldownPie(factory: Metrics.Factory, id: String, values: Supplier<Map<String, Map<String, Int>>>) =
        factory.addMetric(FastStatsBridge.`object`(id.validChartId(), Callable {
            JsonObject().also { root ->
                values.get().forEach { (group, entries) ->
                    root.add(group, JsonObject().also { child -> entries.forEach { (key, value) -> child.addProperty(key, value) } })
                }
            }
        }))

    fun singleLineChart(factory: Metrics.Factory, id: String, value: Supplier<Int>) =
        factory.addMetric(FastStatsBridge.number(id.validChartId(), Callable { value.get() }))

    fun advancedBarChart(factory: Metrics.Factory, id: String, values: Supplier<Map<String, IntArray>>) =
        factory.addMetric(FastStatsBridge.`object`(id.validChartId(), Callable {
            JsonObject().also { root ->
                values.get().forEach { (key, numbers) ->
                    root.add(key, JsonArray().also { array -> numbers.forEach(array::add) })
                }
            }
        }))
}

/** Sends pnLibrary telemetry errors to the FastStats tracker. */
class FastStatsErrorReporter : ErrorReporter {
    val tracker = FastStatsBridge.errorTracker()

    override fun capture(error: TelemetryError) {
        val throwable = RuntimeException("${error.type}: ${error.message}").apply {
            stackTrace = error.stackTrace.map { frame ->
                StackTraceElement("reported", frame, null, -1)
            }.toTypedArray()
        }
        val tracked: TrackedError = tracker.trackError(throwable)
        tracked.handled(error.handled)
        error.operation?.let { tracked.attributes().put("operation", it) }
        error.attributes.forEach { (key, value) -> value?.let { tracked.attributes().put(key, it.toString()) } }
    }

    /** Nothing to release: the FastStats tracker lives for the whole process. */
    override fun close() = Unit
}
