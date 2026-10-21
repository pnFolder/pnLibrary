package org.pnfolder.pnlibrary.core.plugin

import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticContainer
import org.pnfolder.pnlibrary.api.downloads.FileDownloads
import org.pnfolder.pnlibrary.api.plugin.MetricsBuilder
import org.pnfolder.pnlibrary.api.plugin.PluginBuilder
import org.pnfolder.pnlibrary.api.plugin.PluginDependency
import org.pnfolder.pnlibrary.api.plugin.PluginMetadataBuilder
import org.pnfolder.pnlibrary.api.plugin.PluginOptions
import org.pnfolder.pnlibrary.api.plugin.RemotePolicySettings
import org.pnfolder.pnlibrary.api.updates.PluginUpdateRequest
import org.pnfolder.pnlibrary.api.updates.ProductDescriptor
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
    var remotePolicy: RemotePolicySettings? = null
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
        require(productDescriptor == null) { "Component descriptor is already configured" }
        productDescriptor = descriptor
    }

    override fun depends(dependency: PluginDependency): PluginBuilder = apply {
        require(dependency.managed != null || dependency.external != null) {
            "Plugin dependency must provide a managed or external dependency"
        }
        require(dependencies.none { it.refersToSameProductAs(dependency) }) {
            "Duplicate plugin dependency"
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
        require(downloadsRequest == null) { "Downloads are already configured" }
        downloadsRequest = request
    }

    override fun remotePolicy(policy: RemotePolicySettings): PluginBuilder = apply {
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
            .also { target -> source.configurationBackup?.let { target.configurationBackup(it.root, *it.files.toTypedArray()) } }
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
            require(value.isNotEmpty()) { "Plugin metadata $field must not be blank" }
        }
    }
}
