package ru.privatenull.pnlibrary.api.plugin

import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.api.metrics.MetricsProviderConfiguration
import ru.privatenull.pnlibrary.api.metrics.ErrorReporter
import java.util.function.Consumer

/**
 * Runtime control over one plugin's optional metrics session.
 *
 * A configured project ID is retained while metrics are disabled, allowing [enable] to restart the
 * same session. Closing the controller disables the session permanently with its plugin context.
 */
interface MetricsController : AutoCloseable {
    /** Whether this controller has been permanently released with its module context. */
    val isClosed: Boolean get() = false
    /** Whether a metrics session is currently active. */
    val isEnabled: Boolean
    /** Configured bStats project ID, or `null` when metrics were not configured. */
    val projectId: Int?

    /** Configured provider backends, including disabled entries retained for status reporting. */
    val providerConfigurations: List<MetricsProviderConfiguration>
        get() = emptyList()

    /** Returns the configured error reporter, or null when no provider supports error tracking. */
    fun errorReporterOrNull(): ErrorReporter? = null

    /** Enables metrics using the configured [projectId]. */
    fun enable()

    /** Sets [projectId] and enables metrics. */
    fun enable(projectId: Int)

    /** Closes the active metrics session without forgetting its configuration. */
    fun disable()

    /** Changes the project ID and restarts the session when it is enabled. */
    fun changeProjectId(projectId: Int)

    /** Applies [configure] now and again whenever the session is restarted. */
    fun configure(configure: Consumer<PluginMetrics>)

    /** Disables the active session and releases this controller. */
    override fun close()
}
