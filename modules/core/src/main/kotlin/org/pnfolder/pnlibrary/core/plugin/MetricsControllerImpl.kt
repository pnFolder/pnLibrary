package org.pnfolder.pnlibrary.core.plugin

import org.pnfolder.pnlibrary.api.metrics.ErrorReporter
import org.pnfolder.pnlibrary.api.metrics.MetricsProviderConfiguration
import org.pnfolder.pnlibrary.api.metrics.MetricsService
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics
import org.pnfolder.pnlibrary.api.metrics.MetricsController
import java.util.concurrent.atomic.AtomicBoolean
import java.util.function.Consumer

/** Enables, reconfigures and closes one module's metrics session. */
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
        checkOpen()
        if (session != null) return@synchronized
        session = openSession()
    }

    override fun disable() = synchronized(lock) {
        checkOpen()
        closeSession()
    }

    override fun update(configuration: MetricsProviderConfiguration) = synchronized(lock) {
        checkOpen()
        val index = configuredProviders.indexOfFirst { it.provider == configuration.provider }
        if (index >= 0 && configuredProviders[index] == configuration) return@synchronized
        if (index < 0) configuredProviders += configuration else configuredProviders[index] = configuration
        if (session != null) restart()
    }

    override fun configure(configure: Consumer<PluginMetrics>) {
        synchronized(lock) {
            checkOpen()
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

    private fun checkOpen() = check(!closed.get()) { "Metrics controller is closed" }
}
