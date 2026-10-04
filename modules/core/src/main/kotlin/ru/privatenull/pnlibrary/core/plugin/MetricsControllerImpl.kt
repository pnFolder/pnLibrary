package ru.privatenull.pnlibrary.core.plugin

import ru.privatenull.pnlibrary.api.metrics.MetricsService
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.api.metrics.MetricsProviderConfiguration
import ru.privatenull.pnlibrary.api.metrics.ErrorReporter
import ru.privatenull.pnlibrary.api.plugin.MetricsController
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

internal class MetricsControllerImpl(
    private val owner: Any,
    private val service: MetricsService,
    initialProjectId: Int?,
    initiallyEnabled: Boolean,
    initialConfigurers: List<Consumer<PluginMetrics>>,
    initialProviders: List<MetricsProviderConfiguration> = emptyList(),
    private val errorReporter: ErrorReporter? = null,
) : MetricsController {

    private val lock = Any()
    private val closed = AtomicBoolean(false)
    private val configurers = initialConfigurers.toMutableList()
    private val configuredProviders = initialProviders.toMutableList()
    @Volatile
    private var currentProjectId: Int? = initialProjectId

    @Volatile
    private var session: PluginMetrics? = null

    init {
        initialProjectId?.let { require(it > 0) { "metrics projectId must be positive" } }
        if (initiallyEnabled) enable()
    }

    override val isEnabled: Boolean get() = session != null
    override val isClosed: Boolean get() = closed.get()
    override val projectId: Int? get() = currentProjectId
    override val providerConfigurations: List<MetricsProviderConfiguration>
        get() = configuredProviders.toList()

    override fun errorReporterOrNull(): ErrorReporter? =
        session?.errorReporter?.let { errorReporter ?: it }

    override fun fastStatsOrNull(): ru.privatenull.pnlibrary.api.metrics.FastStatsFacade? {
        val backend = session?.fastStatsOrNull() ?: return null
        return object : ru.privatenull.pnlibrary.api.metrics.FastStatsFacade {
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
        currentProjectId = projectId
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
        currentProjectId = projectId
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
        configuredProviders.replaceAll { configuration ->
            if (configuration.provider == ru.privatenull.pnlibrary.api.metrics.MetricsProvider.BSTATS) {
                configuration.copy(projectId = projectId)
            } else configuration
        }
    }
}
