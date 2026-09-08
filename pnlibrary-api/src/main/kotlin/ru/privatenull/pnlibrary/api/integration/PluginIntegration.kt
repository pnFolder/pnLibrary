package ru.privatenull.pnlibrary.api.integration

import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticContainer
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticRegistration
import ru.privatenull.pnlibrary.api.events.EventScope
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.api.updates.PluginUpdateRequest
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.api.tasks.TaskScope
import ru.privatenull.pnlibrary.api.updates.UpdateRegistration
import java.nio.file.Path
import java.util.function.Consumer

/**
 * Unified pnLibrary registration for one consumer plugin.
 *
 * This object groups events, tasks, metrics, diagnostics, and updates. The owner only
 * needs to keep this integration and call [close] during plugin shutdown.
 *
 * Kotlin:
 * ```kotlin
 * val integration = PluginIntegration.builder(library, plugin, "pnclans")
 *     .metrics(33208) { it.simplePie("storage") { "SQLITE" } }
 *     .diagnostics(plugin.dataFolder.toPath(), container)
 *     .updates(updateRequest)
 *     .build()
 * ```
 *
 * Java:
 * ```java
 * PluginIntegration integration = PluginIntegration
 *     .builder(library, plugin, "example")
 *     .metrics(12345, metrics -> metrics.simplePie("mode", () -> "DEFAULT"))
 *     .diagnostics(plugin.getDataFolder().toPath(), container)
 *     .updates(updateRequest)
 *     .build();
 * ```
 */
class PluginIntegration private constructor(
    /** Owner-bound task scope cancelled by [close]. */
    val tasks: TaskScope,
    /** Owner-bound event scope whose subscriptions are removed by [close]. */
    val events: EventScope,
    /** Plugin metrics, or `null` when metrics were not configured. */
    val metrics: PluginMetrics?,
    /** Diagnostics registration, or `null`. */
    val diagnostics: DiagnosticRegistration?,
    /** Update registration, or `null`. */
    val updates: UpdateRegistration?,
    private val diagnosticsCleanup: (() -> Unit)?,
) : AutoCloseable {

    /** Closes updates, diagnostics, metrics, events, and tasks in a safe order. */
    override fun close() {
        runCatching { updates?.close() }
        runCatching { diagnostics?.close() }
        runCatching { diagnosticsCleanup?.invoke() }
        runCatching { metrics?.close() }
        runCatching { events.close() }
        runCatching { tasks.close() }
    }

    /** Cross-platform builder for a unified plugin integration. */
    class Builder internal constructor(
        private val library: PnLibrary,
        private val owner: Any,
        private val pluginId: String,
    ) {
        private var metricsProjectId: Int? = null
        private var metricsConfigurer: Consumer<PluginMetrics>? = null
        private var diagnosticsDirectory: Path? = null
        private var diagnosticContainer: DiagnosticContainer? = null
        private var updateRequest: PluginUpdateRequest? = null

        /** Opens metrics and configures charts through [configure]. */
        fun metrics(projectId: Int, configure: Consumer<PluginMetrics>): Builder = apply {
            require(projectId > 0) { "projectId must be positive" }
            metricsProjectId = projectId
            metricsConfigurer = configure
        }

        /** Registers a diagnostics container and its allowed data directory. */
        fun diagnostics(dataDirectory: Path, container: DiagnosticContainer): Builder = apply {
            diagnosticsDirectory = dataDirectory
            diagnosticContainer = container
        }

        /** Registers update checks and downloads for this plugin. */
        fun updates(request: PluginUpdateRequest): Builder = apply { updateRequest = request }

        /** Creates all selected subsystems as one failure-safe registration. */
        fun build(): PluginIntegration {
            require(pluginId.matches(Regex("[A-Za-z0-9_.-]+"))) { "invalid pluginId" }
            val tasks = library.tasks.scope(owner)
            var events: EventScope? = null
            var metrics: PluginMetrics? = null
            var diagnostics: DiagnosticRegistration? = null
            var updates: UpdateRegistration? = null
            try {
                val eventScope = library.events.scope(owner).also { events = it }
                metricsProjectId?.let { projectId ->
                    metrics = library.metrics.open(owner, projectId).also { metricsConfigurer?.accept(it) }
                }
                diagnosticContainer?.let { container ->
                    diagnostics = library.diagnostics.register(
                        pluginId,
                        requireNotNull(diagnosticsDirectory),
                        container,
                    )
                }
                updateRequest?.let { updates = library.updates.register(owner, it) }
                return PluginIntegration(
                    tasks,
                    eventScope,
                    metrics,
                    diagnostics,
                    updates,
                    diagnosticContainer?.let { { library.diagnostics.clearPlugin(pluginId) } },
                )
            } catch (error: Throwable) {
                runCatching { updates?.close() }
                runCatching { diagnostics?.close() }
                runCatching { metrics?.close() }
                runCatching { events?.close() }
                runCatching { tasks.close() }
                throw error
            }
        }
    }

    companion object {
        /** Starts an integration for [pluginId] owned by [owner]. */
        @JvmStatic
        fun builder(library: PnLibrary, owner: Any, pluginId: String): Builder =
            Builder(library, owner, pluginId)
    }
}
