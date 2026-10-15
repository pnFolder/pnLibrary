package ru.privatenull.pnlibrary.core.plugin

import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticContainer
import ru.privatenull.pnlibrary.api.downloads.FileDownloads
import ru.privatenull.pnlibrary.api.plugin.MetricsBuilder
import ru.privatenull.pnlibrary.api.plugin.PluginBuilder
import ru.privatenull.pnlibrary.api.plugin.PluginDependency
import ru.privatenull.pnlibrary.api.plugin.PluginMetadataBuilder
import ru.privatenull.pnlibrary.api.plugin.PluginOptions
import ru.privatenull.pnlibrary.api.plugin.RemotePolicy
import ru.privatenull.pnlibrary.api.updates.PluginUpdateRequest
import ru.privatenull.pnlibrary.api.updates.ProductDescriptor
import java.nio.file.Path
import java.util.function.Consumer

/**
 * Mutable definition assembled by the public [PluginBuilder] DSL.
 *
 * The registry consumes this object only after the caller has finished configuring
 * a module. Keeping the definition separate makes registration orchestration and
 * user-facing configuration two distinct responsibilities.
 */
internal class ModuleDefinitionBuilder : PluginBuilder {
    var remotePolicy: RemotePolicy? = null
    var metadataName: String? = null
    var metadataVersion: String? = null
    var metadataAuthors: String? = null
    var metrics: ModuleMetricsDefinition = ModuleMetricsDefinition()
        private set
    var diagnosticsDirectory: Path? = null
    var diagnosticContainer: DiagnosticContainer? = null
    var updateRequest: PluginUpdateRequest? = null
    var productDescriptor: ProductDescriptor? = null
    val dependencies = mutableListOf<PluginDependency>()
    var downloadsRequest: FileDownloads? = null
    var placeholderApiEnabled: Boolean = true

    override fun product(descriptor: ProductDescriptor): PluginBuilder = apply {
        require(productDescriptor == null) { "component descriptor is already configured" }
        productDescriptor = descriptor
    }

    override fun depends(dependency: PluginDependency): PluginBuilder = apply {
        require(dependency.managed != null || dependency.external != null) {
            "plugin dependency must provide a managed or external dependency"
        }
        require(dependencies.none { it.refersToSameProductAs(dependency) }) {
            "duplicate plugin dependency"
        }
        dependencies += dependency
    }

    override fun metadata(configure: Consumer<PluginMetadataBuilder>): PluginBuilder = apply {
        configure.accept(MetadataDefinition(this))
    }

    override fun options(configure: Consumer<PluginOptions>): PluginBuilder = apply {
        configure.accept(ModuleOptions(this))
    }

    /** Accumulates provider declarations through the shared metrics DSL. */
    override fun metrics(configure: Consumer<MetricsBuilder>): PluginBuilder = apply {
        val builder = MetricsBuilder()
        configure.accept(builder)
        metrics = ModuleMetricsDefinition(
            providers = metrics.providers + builder.build(),
            enabled = builder.enabled,
            configurers = metrics.configurers + Consumer(builder::configure),
        )
    }

    override fun diagnostics(dataDirectory: Path, container: DiagnosticContainer): PluginBuilder = apply {
        diagnosticsDirectory = dataDirectory
        diagnosticContainer = container
    }

    override fun updates(request: PluginUpdateRequest): PluginBuilder = apply {
        updateRequest = request
    }

    override fun downloads(request: FileDownloads): PluginBuilder = apply {
        require(downloadsRequest == null) { "downloads are already configured" }
        downloadsRequest = request
    }

    override fun remotePolicy(policy: RemotePolicy): PluginBuilder = apply {
        remotePolicy = policy
    }

    fun bindUpdatesTo(descriptor: ProductDescriptor?) {
        val source = updateRequest ?: return
        descriptor ?: return

        updateRequest = PluginUpdateRequest.builder()
            .repository(source.repositoryOwner, source.repositoryName)
            .channel(source.channel)
            .automaticDownload(source.automaticDownload)
            .supportedApi(descriptor.supportedApi.minimum, descriptor.supportedApi.maximum)
            .also { target -> copyArtifactRules(source, target) }
            .build()
    }

    private fun copyArtifactRules(
        source: PluginUpdateRequest,
        target: PluginUpdateRequest.Builder,
    ) {
        source.artifacts.forEach { artifact ->
            val platform = artifact.platform
            if (platform == null) {
                target.artifact(artifact.pattern, artifact.minimumJava, artifact.maximumJava)
            } else {
                target.artifact(artifact.pattern, platform, artifact.minimumJava, artifact.maximumJava)
            }
        }
    }

    private fun PluginDependency.refersToSameProductAs(other: PluginDependency): Boolean {
        val managedDependency = managed
        val externalDependency = external
        val sameManagedProduct = managedDependency != null &&
            managedDependency.product == other.managed?.product
        val sameExternalPlugin = externalDependency != null &&
            externalDependency.plugin.equals(other.external?.plugin, ignoreCase = true)
        return sameManagedProduct || sameExternalPlugin
    }

    private class ModuleOptions(
        private val definition: ModuleDefinitionBuilder,
    ) : PluginOptions {
        override fun placeholderApi(enabled: Boolean): PluginOptions = apply {
            definition.placeholderApiEnabled = enabled
        }
    }

    private class MetadataDefinition(
        private val definition: ModuleDefinitionBuilder,
    ) : PluginMetadataBuilder {
        override fun name(value: String): PluginMetadataBuilder = apply {
            definition.metadataName = value.requireMetadata("name")
        }

        override fun version(value: String): PluginMetadataBuilder = apply {
            definition.metadataVersion = value.requireMetadata("version")
        }

        override fun authors(value: String): PluginMetadataBuilder = apply {
            definition.metadataAuthors = value.requireMetadata("authors")
        }

        private fun String.requireMetadata(field: String): String = trim().also { value ->
            require(value.isNotEmpty()) { "plugin metadata $field must not be blank" }
        }
    }
}
