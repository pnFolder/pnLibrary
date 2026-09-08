package ru.privatenull.pnlibrary.api.plugin

import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticContainer
import ru.privatenull.pnlibrary.api.events.Listener
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.api.updates.PluginUpdateRequest
import java.nio.file.Path
import java.util.function.Consumer

/** Declarative setup used by [PluginRegistry.register]. */
interface PluginBuilder {
    /** Enables or disables automatic startup and shutdown summaries. Enabled by default. */
    fun lifecycleMessages(enabled: Boolean): PluginBuilder

    /** Overrides native metadata only when a plugin needs custom display values. */
    fun metadata(configure: Consumer<PluginMetadataBuilder>): PluginBuilder

    /** Adds plugin-specific rows to the automatic enabled and disabled messages. */
    fun lifecycle(configure: Consumer<PluginLifecycleBuilder>): PluginBuilder

    fun metrics(projectId: Int): PluginBuilder = metrics(projectId, true, Consumer { })
    fun metrics(projectId: Int, enabled: Boolean): PluginBuilder =
        metrics(projectId, enabled, Consumer { })
    fun metrics(projectId: Int, configure: Consumer<PluginMetrics>): PluginBuilder =
        metrics(projectId, true, configure)
    fun metrics(projectId: Int, enabled: Boolean, configure: Consumer<PluginMetrics>): PluginBuilder

    fun diagnostics(dataDirectory: Path, container: DiagnosticContainer): PluginBuilder
    fun updates(request: PluginUpdateRequest): PluginBuilder

    /** Builds the update request inline instead of requiring a temporary variable. */
    fun updates(configure: Consumer<PluginUpdateRequest.Builder>): PluginBuilder {
        val builder = PluginUpdateRequest.builder()
        configure.accept(builder)
        return updates(builder.build())
    }

    /** Inline GitHub updater configuration with the repository already filled in. */
    fun updates(
        repositoryOwner: String,
        repositoryName: String,
        configure: Consumer<PluginUpdateRequest.Builder>,
    ): PluginBuilder {
        val builder = PluginUpdateRequest.builder().repository(repositoryOwner, repositoryName)
        configure.accept(builder)
        return updates(builder.build())
    }

    fun listener(listener: Listener): PluginBuilder
}
