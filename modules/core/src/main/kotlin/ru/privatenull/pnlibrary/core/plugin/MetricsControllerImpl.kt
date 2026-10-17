package ru.privatenull.pnlibrary.core.plugin

import ru.privatenull.pnlibrary.api.metrics.ErrorReporter
import ru.privatenull.pnlibrary.api.metrics.MetricsProviderConfiguration
import ru.privatenull.pnlibrary.api.metrics.MetricsService
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.api.plugin.MetricsController
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

    @Volatile
    private var session: PluginMetrics? = null

    init {
        if (definition.enabled) enable()
    }

    override val isEnabled: Boolean get() = session != null
    override val isClosed: Boolean get() = closed.get()
    override val providerConfigurations: List<MetricsProviderConfiguration>
        get() = synchronized(lock) { configuredProviders.toList() }

    override fun errorReporterOrNull(): ErrorReporter? =
        session?.errorReporter?.let { errorReporter ?: it }

    /** Returns the reporter owned by the live provider session. */
    internal fun sessionErrorReporter(): ErrorReporter? = session?.errorReporter

    override fun enable() = synchronized(lock) {
        ensureOpen()
        if (session != null) return@synchronized
        session = openSession()
    }

    override fun disable() = synchronized(lock) {
        ensureOpen()
        closeSession()
    }

    override fun update(configuration: MetricsProviderConfiguration) = synchronized(lock) {
        ensureOpen()
        val index = configuredProviders.indexOfFirst { it.provider == configuration.provider }
        if (index >= 0 && configuredProviders[index] == configuration) return@synchronized
        if (index < 0) configuredProviders += configuration else configuredProviders[index] = configuration
        if (session != null) restart()
    }

    override fun configure(configure: Consumer<PluginMetrics>) {
        synchronized(lock) {
            ensureOpen()
            configurers += configure
            if (session != null) restart()
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(lock) {
            closeSession()
            configurers.clear()
        }
    }

    private fun restart() {
        closeSession()
        session = openSession()
    }

    private fun closeSession() {
        session?.close()
        session = null
    }

    private fun openSession(): PluginMetrics {
        val enabled = configuredProviders.filter { it.enabled }
        require(enabled.isNotEmpty()) { "At least one metrics provider must be configured and enabled" }
        val opened = service.open(owner, enabled)
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
}
