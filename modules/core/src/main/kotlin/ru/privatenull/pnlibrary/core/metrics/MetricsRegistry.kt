package ru.privatenull.pnlibrary.core.metrics

import ru.privatenull.pnlibrary.api.metrics.ErrorReporter
import ru.privatenull.pnlibrary.api.metrics.MetricsProviderConfiguration
import ru.privatenull.pnlibrary.api.metrics.MetricsService
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Owns every platform metrics session and provides idempotent managed close handles. */
internal class MetricsRegistry(private val factory: PlatformMetricsFactory) : MetricsService, AutoCloseable {
    private var closed = false

    @Synchronized
    override fun openErrorReporter(owner: Any, configurations: Collection<MetricsProviderConfiguration>): ErrorReporter? {
        check(!closed) { "MetricsRegistry is closed" }
        return factory.openErrorReporter(owner, configurations)
    }
    private val sessions = Collections.newSetFromMap(IdentityHashMap<PluginMetrics, Boolean>())

    @Synchronized
    override fun open(
        owner: Any,
        configurations: Collection<MetricsProviderConfiguration>,
    ): PluginMetrics {
        check(!closed) { "MetricsRegistry is closed" }
        require(configurations.any { it.enabled }) {
            "At least one metrics provider must be enabled"
        }
        return manage(factory.open(owner, configurations))
    }

    private fun manage(delegate: PluginMetrics): PluginMetrics {
        val managed = ManagedMetrics(delegate) { synchronized(this) { sessions.remove(it) } }
        sessions.add(managed)
        return managed
    }

    @Synchronized
    fun diagnosticSnapshot(): List<Map<String, Any?>> = sessions.map { session ->
        linkedMapOf(
            "providers" to session.providers.map { it.name },
            "errorReporter" to (session.errorReporter != null),
            "errorPipeline" to (session.errorReporter as? ErrorPipeline)?.diagnosticSnapshot(),
        )
    }

    @Synchronized
    fun diagnosticAnalytics(): Map<String, Any?> {
        val snapshots = diagnosticSnapshot()
        val pipelines = snapshots.mapNotNull { it["errorPipeline"] as? Map<*, *> }
        return linkedMapOf(
            "sessionCount" to snapshots.size,
            "providers" to snapshots.flatMap { (it["providers"] as? Collection<*>) ?: emptyList<Any?>() }
                .mapNotNull { it?.toString() }
                .groupingBy { it }
                .eachCount(),
            "errorReporterCount" to snapshots.count { it["errorReporter"] == true },
            "errorPipelineCount" to pipelines.size,
            "reportedErrors" to pipelines.sumOf { (it["reportedErrors"] as? Number)?.toLong() ?: 0L },
        )
    }

    @Synchronized
    override fun close() {
        if (closed) return
        closed = true
        sessions.toList().forEach { runCatching { it.close() } }
        sessions.clear()
    }
}

/** Removes a delegate from its registry exactly once, even when delegate close fails. */
private class ManagedMetrics(
    private val delegate: PluginMetrics,
    private val onClose: (PluginMetrics) -> Unit,
) : PluginMetrics by delegate {
    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            delegate.close()
        } finally {
            onClose(this)
        }
    }
}
