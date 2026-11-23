package org.pnfolder.pnlibrary.api.plugin

import org.pnfolder.pnlibrary.api.compat.MinecraftSupportDeclaration
import org.pnfolder.pnlibrary.api.dependencies.DependencyBuilder
import org.pnfolder.pnlibrary.api.dependencies.PluginDependency
import org.pnfolder.pnlibrary.api.diagnostics.report.DiagnosticContainer
import org.pnfolder.pnlibrary.api.downloads.FileDownloads
import org.pnfolder.pnlibrary.api.libraries.ModuleLibrary
import org.pnfolder.pnlibrary.api.metrics.provider.MetricsBuilder
import org.pnfolder.pnlibrary.api.plugin.metadata.PluginMetadataBuilder
import org.pnfolder.pnlibrary.api.plugin.metadata.PluginOptions
import org.pnfolder.pnlibrary.api.remote.RemotePolicySettings
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateRequest
import java.nio.file.Path
import java.util.function.BooleanSupplier
import java.util.function.Consumer

/**
 * Declarative setup evaluated by [PluginRegistry.register] before capabilities become visible.
 *
 * Builder calls only describe the context. pnLibrary creates integrations after the registration
 * callback returns and rolls back partial resources if any integration fails to initialize.
 */
interface PluginBuilder : MinecraftSupportDeclaration {
    /** Declares one dependency of the plugin. */
    fun depends(dependency: PluginDependency): PluginBuilder

    /** Declares several dependencies of the plugin. */
    fun depends(vararg dependencies: PluginDependency): PluginBuilder {
        dependencies.forEach(::depends)
        return this
    }

    /**
     * Asks pnLibrary to download, verify and load a Maven library for this module before the module starts, so the
     * plugin needs no `libraries:` block in `plugin.yml`. A required library that cannot be loaded makes `registerModule`
     * throw; an optional one only logs a warning. The same library declared twice is loaded once.
     *
     * ```kotlin
     * registration.registerModule(MODULE_ID) { setup ->
     *     setup.library("org.xerial:sqlite-jdbc:3.46.0.0")
     * }
     * ```
     */
    fun library(library: ModuleLibrary): PluginBuilder

    /** Declares several libraries; see [library]. */
    fun libraries(vararg libraries: ModuleLibrary): PluginBuilder {
        libraries.forEach { library(it) }
        return this
    }

    /**
     * Declares a library that is loaded only when [enabled] answers `true` at the moment the module starts, for example
     * `setup.library(sqlite) { settings.storageType == "SQLITE" }`. A library that is not needed is not downloaded and not listed.
     */
    fun library(library: ModuleLibrary, enabled: BooleanSupplier): PluginBuilder

    /**
     * Settings every library declared afterwards by coordinates or inline starts from, such as your own mirror, Maven
     * Central off or an isolated class loader. The library's own settings come after and win. A ready [ModuleLibrary] is not changed.
     *
     * ```kotlin
     * setup.libraryDefaults { it.source { repository("https://mirror.example.org/maven/") }.mavenCentral(false) }
     * setup.library("org.xerial:sqlite-jdbc:3.46.0.0")
     * ```
     */
    fun libraryDefaults(configure: Consumer<ModuleLibrary.Builder>): PluginBuilder

    /** Declares a library by `group:artifact:version` (and an optional classifier) with the defaults. */
    fun library(coordinates: String): PluginBuilder {
        val parts = coordinates.trim().split(':')
        require(parts.size == 3 || parts.size == 4) { "Expected group:artifact:version, got: $coordinates" }
        return library(parts[0], parts[1], parts[2], Consumer { builder -> parts.getOrNull(3)?.let(builder::classifier) })
    }

    /** Builds a library inline: `setup.library("org.xerial", "sqlite-jdbc", "3.46.0.0") { it.title("SQLite") }`. */
    fun library(group: String, artifact: String, version: String, configure: Consumer<ModuleLibrary.Builder>): PluginBuilder

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

    /** Declares dependencies with the dependency DSL. */
    fun dependencies(configure: Consumer<DependencyBuilder>): PluginBuilder {
        val builder = DependencyBuilder()
        configure.accept(builder)
        return depends(*builder.build().toTypedArray())
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

    /** Registers auxiliary file deliveries. Plugins belong in [depends], this product's release in [updates]. */
    fun downloads(request: FileDownloads): PluginBuilder

    /** Attaches one validated reusable remote policy declaration. */
    fun remotePolicy(policy: RemotePolicySettings): PluginBuilder

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
