package org.pnfolder.pnlibrary.core.downloads.dependencies

import org.pnfolder.pnlibrary.api.dependencies.PluginDependency
import org.pnfolder.pnlibrary.api.dependencies.external.ExternalArtifact
import org.pnfolder.pnlibrary.api.dependencies.external.ExternalPluginDependency
import org.pnfolder.pnlibrary.api.downloads.source.DownloadDestination
import org.pnfolder.pnlibrary.api.downloads.FileDownloads
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.version.SemanticVersion
import java.nio.file.Path

/** Download request for missing plugin dependencies with the requirement each downloaded JAR must meet. */
internal data class DependencyDownloadRequest(
    val request: FileDownloads,
    val requirements: Map<String, ExternalPluginDependency>,
)

/** [platform] and [minecraft] pick, among a dependency's files, the one made for this server. */
internal class DependencyDownloadRequestFactory(
    private val serverUpdateFolder: Path,
    private val platform: PlatformType,
    private val minecraft: () -> String?,
) {
    /** The file of [dependency] meant for this server, or null when it has none (only a page, or no variant fits). */
    fun fileFor(dependency: ExternalPluginDependency): ExternalArtifact? = dependency.artifactFor(platform, minecraft())

    /** The external dependencies that are not installed at a fitting version. */
    fun missing(dependencies: List<PluginDependency>, installedPlugins: Map<String, String>): List<ExternalPluginDependency> =
        dependencies.mapNotNull { it.external }.filterNot { isAlreadyCompatible(it, installedPlugins) }

    fun create(
        dependencies: List<PluginDependency>,
        installedPlugins: Map<String, String>,
    ): DependencyDownloadRequest? {
        val downloadable = dependencies.mapNotNull { dependency ->
            val external = dependency.external ?: return@mapNotNull null
            val artifact = fileFor(external) ?: return@mapNotNull null
            if (isAlreadyCompatible(external, installedPlugins)) return@mapNotNull null
            Triple(dependency, external, artifact)
        }
        if (downloadable.isEmpty()) return null

        val request = FileDownloads.builder()
            .dataDirectory(serverUpdateFolder)
            .also { builder ->
                downloadable.forEach { (dependency, external, artifact) ->
                    val key = dependencyKey(external.plugin)
                    builder.file(key) { file ->
                        file.url(artifact.uri.toString())
                            .policy(dependency.downloadPolicy)
                            .destination(DownloadDestination.DATA_FOLDER, safeFileName(external.plugin))
                        if (!dependency.required) file.optional()
                        artifact.mirrors.forEach { file.mirror(it.toString()) }
                        artifact.sha256?.let { file.sha256(it) }
                        artifact.maxBytes?.let { file.maxBytes(it) }
                    }
                }
            }
            .build()
        return DependencyDownloadRequest(
            request,
            downloadable.associate { (_, external, _) -> dependencyKey(external.plugin) to external },
        )
    }

    private fun isAlreadyCompatible(
        dependency: ExternalPluginDependency,
        installedPlugins: Map<String, String>,
    ): Boolean {
        val installedVersion = installedPlugins.entries
            .firstOrNull { it.key.equals(dependency.plugin, ignoreCase = true) }
            ?.value
            ?: return false
        return SemanticVersion.tryParse(installedVersion)?.let(dependency.versions::accepts) == true
    }

    private fun dependencyKey(plugin: String): String = "dependency:$plugin"

    private fun safeFileName(plugin: String): String {
        val safeName = plugin.replace(Regex("[^A-Za-z0-9._-]+"), "-")
            .trim('-', '.')
            .ifBlank { "plugin" }
        return "$safeName.jar"
    }
}
