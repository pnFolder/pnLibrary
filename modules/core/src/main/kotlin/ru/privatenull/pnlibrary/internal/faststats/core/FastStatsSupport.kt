package ru.privatenull.pnlibrary.internal.faststats.core

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dev.faststats.Metrics
import dev.faststats.TrackedError
import ru.privatenull.pnlibrary.api.metrics.ErrorReporter
import ru.privatenull.pnlibrary.api.metrics.TelemetryError
import ru.privatenull.pnlibrary.internal.faststats.FastStatsBridge
import java.util.concurrent.Callable
import java.util.function.Supplier

/** Shared, platform-neutral FastStats helpers used by every platform adapter. */
object FastStatsSupport {
    /** Validates and normalizes a metric identifier accepted by FastStats. */
    @JvmStatic
    fun validId(id: String): String = id.trim().also {
        require(it.matches(Regex("[A-Za-z0-9_-]{1,64}"))) { "Invalid FastStats metric id: $id" }
    }

    fun simplePie(factory: Metrics.Factory, id: String, value: Supplier<String?>) =
        factory.addMetric(FastStatsBridge.string(validId(id), Callable { value.get() ?: "unknown" }))

    fun advancedPie(factory: Metrics.Factory, id: String, values: Supplier<Map<String, Int>>) =
        factory.addMetric(FastStatsBridge.numberMap(validId(id), Callable { values.get() }))

    fun drilldownPie(factory: Metrics.Factory, id: String, values: Supplier<Map<String, Map<String, Int>>>) =
        factory.addMetric(FastStatsBridge.`object`(validId(id), Callable {
            JsonObject().also { root ->
                values.get().forEach { (group, entries) ->
                    root.add(group, JsonObject().also { child -> entries.forEach { (key, value) -> child.addProperty(key, value) } })
                }
            }
        }))

    fun singleLineChart(factory: Metrics.Factory, id: String, value: Supplier<Int>) =
        factory.addMetric(FastStatsBridge.number(validId(id), Callable { value.get() }))

    fun advancedBarChart(factory: Metrics.Factory, id: String, values: Supplier<Map<String, IntArray>>) =
        factory.addMetric(FastStatsBridge.`object`(validId(id), Callable {
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

    override fun close() = Unit
}
