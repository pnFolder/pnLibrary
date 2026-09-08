package ru.privatenull.pnlibrary.api.plugin

import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import java.util.function.Consumer

/** Runtime control over one plugin's metrics session. */
interface MetricsController : AutoCloseable {
    val isEnabled: Boolean
    val projectId: Int?

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

    override fun close()
}
