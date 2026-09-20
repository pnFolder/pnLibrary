package ru.privatenull.pnlibrary.core.metrics

import ru.privatenull.pnlibrary.api.metrics.MetricsService
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Owns every platform metrics session and provides idempotent managed close handles. */
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
