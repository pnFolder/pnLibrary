package ru.privatenull.pnlibrary.api.plugin

import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticContainer
import ru.privatenull.pnlibrary.api.events.Listener
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.api.updates.PluginUpdateRequest
import ru.privatenull.pnlibrary.api.updates.ProductDescriptor
import java.nio.file.Path
import java.util.function.Consumer
import ru.privatenull.pnlibrary.api.downloads.PluginDownloads

/**
 * Declarative setup evaluated by [PluginRegistry.register] before capabilities become visible.
 *
 * Builder calls only describe the context. pnLibrary creates integrations after the registration
 * callback returns and rolls back partial resources if any integration fails to initialize.
 */
interface PluginBuilder {
    /** Adds one managed pnLibrary component or external plugin dependency. */
    fun depends(dependency: PluginDependency): PluginBuilder

    /** Adds several dependencies using the same unified DSL. */
    fun depends(vararg dependencies: PluginDependency): PluginBuilder {
        dependencies.forEach(::depends)
        return this
    }

    /** Declares the component identity, API range, and dependencies used by the graph updater. */
    fun product(descriptor: ProductDescriptor): PluginBuilder

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
     * The resulting handle is exposed as [ModuleContext.diagnostics] and closed with the context.
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

    /** Adds dependencies through the unified product/native-plugin DSL. */
    fun dependencies(configure: Consumer<DependencyBuilder>): PluginBuilder {
        val builder = DependencyBuilder()
        configure.accept(builder)
        return depends(*builder.build().toTypedArray())
    }

    /** Adds one dependency group; singular convenience alias for [dependencies]. */
    fun dependency(configure: Consumer<DependencyBuilder>): PluginBuilder = dependencies(configure)

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

    /** Registers direct component, native-plugin, and ordinary-file deliveries separately from updates. */
    fun downloads(request: PluginDownloads): PluginBuilder

    /** Attaches one validated reusable remote policy declaration. */
    fun remotePolicy(policy: RemotePolicy): PluginBuilder

    /** Compatibility callback for the original non-fluent policy builder. */
    @Suppress("DEPRECATION")
    @Deprecated("Build a RemotePolicy with RemotePolicy.builder() and pass it directly")
    fun remotePolicy(configure: Consumer<RemotePolicyBuilder>): PluginBuilder {
        val builder = RemotePolicyBuilder()
        configure.accept(builder)
        return remotePolicy(builder.build())
    }

    /** Builds direct-download declarations inline. */
    fun downloads(configure: Consumer<PluginDownloads.Builder>): PluginBuilder {
        val builder = PluginDownloads.builder()
        configure.accept(builder)
        return downloads(builder.build())
    }

    /** Builds direct-download declarations rooted at this plugin's data directory. */
    fun downloads(dataDirectory: Path, configure: Consumer<PluginDownloads.Builder>): PluginBuilder {
        val builder = PluginDownloads.builder().dataDirectory(dataDirectory)
        configure.accept(builder)
        return downloads(builder.build())
    }

    /**
     * Legacy listener declaration kept for API 1 binary/source compatibility.
     *
     * New code should register runtime listeners after registration through
     * `context.events.register(listener)` or `context.events.subscribe(...)`.
     */
    @Deprecated(
        "Register listeners through ModuleContext.events after the context has been created",
        ReplaceWith("context.events.register(listener)"),
    )
    fun listener(listener: Listener): PluginBuilder
}
