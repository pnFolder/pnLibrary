package org.pnfolder.pnlibrary.api.metrics

import org.pnfolder.pnlibrary.api.metrics.ErrorReporter
import org.pnfolder.pnlibrary.api.metrics.MetricsProviderConfiguration
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics
import java.util.function.Consumer

/**
 * Runtime control over one plugin's optional metrics session.
 *
 * Provider configurations are retained while metrics are disabled, allowing [enable] to restart the
 * same session. Closing the controller disables the session permanently with its plugin context.
 */
interface MetricsController : AutoCloseable {
    /** Whether this controller has been permanently released with its module context. */
    val isClosed: Boolean get() = false

    /** Whether a metrics session is currently active. */
    val isEnabled: Boolean

    /** Configured provider backends, including disabled entries retained for status reporting. */
    val providerConfigurations: List<MetricsProviderConfiguration>
        get() = emptyList()

    /** Returns the error reporter of the live session, or null when absent or disabled. */
    fun errorReporterOrNull(): ErrorReporter? = null

    /** Starts every enabled configured provider. */
    fun enable()

    /** Closes the active metrics session without forgetting its configuration. */
    fun disable()

    /**
     * Adds or replaces the configuration of [configuration]'s provider, keeping other providers.
     *
     * The session is restarted when metrics are enabled; otherwise the change applies on [enable].
     */
    fun update(configuration: MetricsProviderConfiguration)

    /** Applies [configure] now and again whenever the session is restarted. */
    fun configure(configure: Consumer<PluginMetrics>)

    /** Disables the active session and releases this controller. */
    override fun close()
}
