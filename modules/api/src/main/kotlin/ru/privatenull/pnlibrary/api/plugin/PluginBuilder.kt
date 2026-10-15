package ru.privatenull.pnlibrary.api.plugin

import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticContainer
import ru.privatenull.pnlibrary.api.updates.PluginUpdateRequest
import ru.privatenull.pnlibrary.api.updates.ProductDescriptor
import java.nio.file.Path
import java.util.function.Consumer
import ru.privatenull.pnlibrary.api.downloads.FileDownloads

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

    /**
     * Adds provider-neutral metrics backends without replacing earlier declarations.
     *
     * Each provider may be declared only once. Chart callbacks accumulate and run for all
     * enabled backends. The latest call selects the initial session enabled state.
     */
    fun metrics(configure: Consumer<MetricsBuilder>): PluginBuilder

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

    /** Registers auxiliary file deliveries. Plugins belong in [depends], this product's release in [updates]. */
    fun downloads(request: FileDownloads): PluginBuilder

    /** Attaches one validated reusable remote policy declaration. */
    fun remotePolicy(policy: RemotePolicy): PluginBuilder

    /** Builds direct-download declarations inline. */
    fun downloads(configure: Consumer<FileDownloads.Builder>): PluginBuilder {
        val builder = FileDownloads.builder()
        configure.accept(builder)
        return downloads(builder.build())
    }

    /** Builds direct-download declarations rooted at this plugin's data directory. */
    fun downloads(dataDirectory: Path, configure: Consumer<FileDownloads.Builder>): PluginBuilder {
        val builder = FileDownloads.builder().dataDirectory(dataDirectory)
        configure.accept(builder)
        return downloads(builder.build())
    }

}
