package ru.privatenull.pnlibrary.core.plugin

import ru.privatenull.pnlibrary.api.metrics.ErrorReporter
import ru.privatenull.pnlibrary.api.plugin.MetricsController
import ru.privatenull.pnlibrary.api.metrics.FastStatsFacade
import ru.privatenull.pnlibrary.api.metrics.MetricsService
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.api.metrics.MetricsProviderConfiguration
import ru.privatenull.pnlibrary.api.metrics.MetricsProvider
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

internal class MetricsControllerImpl(
    private val owner: Any,
    private val service: MetricsService,
    definition: ModuleMetricsDefinition,
    private val errorReporter: ErrorReporter? = null,
) : MetricsController {

    private val lock = Any()
    private val closed = AtomicBoolean(false)
    private val configurers = definition.configurers.toMutableList()
    private val configuredProviders = definition.providers.toMutableList()
    private val currentProjectId: Int?
        get() = configuredProviders.firstOrNull { it.provider == MetricsProvider.BSTATS }?.projectId

    @Volatile
    private var session: PluginMetrics? = null

    init {
        currentProjectId?.let { require(it > 0) { "metrics projectId must be positive" } }
        if (definition.enabled) enable()
    }

    override val isEnabled: Boolean get() = session != null
    override val isClosed: Boolean get() = closed.get()
    override val projectId: Int? get() = synchronized(lock) { currentProjectId }
    override val providerConfigurations: List<MetricsProviderConfiguration>
        get() = synchronized(lock) { configuredProviders.toList() }

    override fun errorReporterOrNull(): ErrorReporter? =
        session?.errorReporter?.let { errorReporter ?: it }

    override fun fastStatsOrNull(): FastStatsFacade? {
        val backend = session?.fastStatsOrNull() ?: return null
        return object : FastStatsFacade {
            override val metrics: PluginMetrics = backend.metrics
            override fun errorTracker(): ErrorReporter = errorReporter ?: backend.errorTracker()
        }
    }

    /** Returns the reporter owned by the live provider session. */
    internal fun sessionErrorReporter(): ErrorReporter? = session?.errorReporter

    override fun enable() = synchronized(lock) {
        ensureOpen()
        if (session != null) return@synchronized
        session = openSession()
    }

    override fun enable(projectId: Int) = synchronized(lock) {
        ensureOpen()
        require(projectId > 0) { "metrics projectId must be positive" }
        if (session != null && currentProjectId == projectId) return@synchronized
        session?.close()
        session = null
        updateBStatsProjectId(projectId)
        session = openSession()
    }

    override fun disable() = synchronized(lock) {
        ensureOpen()
        session?.close()
        session = null
    }

    override fun changeProjectId(projectId: Int) = synchronized(lock) {
        ensureOpen()
        require(projectId > 0) { "metrics projectId must be positive" }
        if (currentProjectId == projectId) return@synchronized
        val restart = session != null
        session?.close()
        session = null
        updateBStatsProjectId(projectId)
        if (restart) session = openSession()
    }

    override fun configure(configure: Consumer<PluginMetrics>) {
        synchronized(lock) {
            ensureOpen()
            configurers += configure
            if (session != null) {
                session?.close()
                session = null
                session = openSession()
            }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(lock) {
            session?.close()
            session = null
            configurers.clear()
        }
    }

    private fun openSession(): PluginMetrics {
        val opened = if (configuredProviders.isEmpty()) {
            service.open(owner, requireNotNull(currentProjectId) {
                "Metrics projectId is not configured"
            })
        } else {
            service.open(owner, configuredProviders.filter { it.enabled })
        }
        try {
            configurers.forEach { it.accept(opened) }
            opened.start()
            return opened
        } catch (error: Throwable) {
            runCatching { opened.close() }
            throw error
        }
    }

    private fun ensureOpen() = check(!closed.get()) { "MetricsController is closed" }

    private fun updateBStatsProjectId(projectId: Int) {
        val index = configuredProviders.indexOfFirst { it.provider == MetricsProvider.BSTATS }
        if (index < 0) {
            configuredProviders += MetricsProviderConfiguration(MetricsProvider.BSTATS, projectId = projectId)
        } else {
            configuredProviders[index] = configuredProviders[index].copy(projectId = projectId, enabled = true)
        }
    }
}
