package ru.privatenull.pnlibrary.api.plugin

import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticContainer
import ru.privatenull.pnlibrary.api.events.Listener
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.api.updates.PluginUpdateRequest
import java.nio.file.Path
import java.util.function.Consumer

/**
 * Declarative setup evaluated by [PluginRegistry.register] before capabilities become visible.
 *
 * Builder calls only describe the context. pnLibrary creates integrations after the registration
 * callback returns and rolls back partial resources if any integration fails to initialize.
 */
interface PluginBuilder {
    /** Overrides native metadata only when a plugin needs custom display values. */
    fun metadata(configure: Consumer<PluginMetadataBuilder>): PluginBuilder

    /** Changes optional integration switches. Unspecified options keep safe defaults. */
    fun options(configure: Consumer<PluginOptions>): PluginBuilder

    /** Configures and enables a bStats session for [projectId]. */
    fun metrics(projectId: Int): PluginBuilder = metrics(projectId, true, Consumer { })
    /** Configures a bStats project and selects its initial enabled state. */
    fun metrics(projectId: Int, enabled: Boolean): PluginBuilder =
        metrics(projectId, enabled, Consumer { })
    /** Configures and enables bStats with custom chart registration. */
    fun metrics(projectId: Int, configure: Consumer<PluginMetrics>): PluginBuilder =
        metrics(projectId, true, configure)

    /**
     * Configures this plugin's metrics integration.
     *
     * [configure] runs whenever the metrics session is created or restarted, so it must tolerate
     * repeated invocation and should only register charts owned by this plugin.
     */
    fun metrics(projectId: Int, enabled: Boolean, configure: Consumer<PluginMetrics>): PluginBuilder

    /**
     * Registers a diagnostics contributor rooted at [dataDirectory].
     *
     * The resulting handle is exposed as [PluginContext.diagnostics] and closed with the context.
     */
    fun diagnostics(dataDirectory: Path, container: DiagnosticContainer): PluginBuilder

    /** Registers one update-check definition for this plugin. */
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

    /**
     * Adds an annotated event [listener] to be registered in the context's [PluginContext.events]
     * scope. Invalid handler methods make the complete plugin registration fail atomically.
     */
    fun listener(listener: Listener): PluginBuilder
}
