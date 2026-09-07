package ru.privatenull.pnlibrary.core.metrics

import ru.privatenull.pnlibrary.api.metrics.MetricsService
import ru.privatenull.pnlibrary.api.metrics.PlatformMetricsFactory
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import java.util.Collections
import java.util.IdentityHashMap

internal class MetricsRegistry(private val factory: PlatformMetricsFactory) : MetricsService, AutoCloseable {
    private val sessions = Collections.newSetFromMap(IdentityHashMap<PluginMetrics, Boolean>())

    @Synchronized
    override fun open(owner: Any, projectId: Int): PluginMetrics {
        require(projectId > 0) { "bStats projectId must be positive" }
        val delegate = factory.open(owner, projectId)
        val managed = ManagedMetrics(delegate) { synchronized(this) { sessions.remove(it) } }
        sessions.add(managed)
        return managed
    }

    @Synchronized
    override fun close() {
        sessions.toList().forEach { runCatching { it.close() } }
        sessions.clear()
    }
}

private class ManagedMetrics(
    private val delegate: PluginMetrics,
    private val onClose: (PluginMetrics) -> Unit,
) : PluginMetrics by delegate {
    private var closed = false
    override fun close() {
        if (closed) return
        closed = true
        try {
            delegate.close()
        } finally {
            onClose(this)
        }
    }
}
