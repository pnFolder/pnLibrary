package ru.privatenull.pnlibrary.api.plugin

import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticContainer
import ru.privatenull.pnlibrary.api.events.Listener
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.api.updates.PluginUpdateRequest
import java.nio.file.Path
import java.util.function.Consumer

/** Declarative setup used by [PluginRegistry.register]. */
interface PluginBuilder {
    fun metrics(projectId: Int): PluginBuilder = metrics(projectId, true, Consumer { })
    fun metrics(projectId: Int, enabled: Boolean): PluginBuilder =
        metrics(projectId, enabled, Consumer { })
    fun metrics(projectId: Int, configure: Consumer<PluginMetrics>): PluginBuilder =
        metrics(projectId, true, configure)
    fun metrics(projectId: Int, enabled: Boolean, configure: Consumer<PluginMetrics>): PluginBuilder

    fun diagnostics(dataDirectory: Path, container: DiagnosticContainer): PluginBuilder
    fun updates(request: PluginUpdateRequest): PluginBuilder
    fun listener(listener: Listener): PluginBuilder
}
