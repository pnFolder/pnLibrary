package ru.privatenull.pnlibrary.core.downloads

import ru.privatenull.pnlibrary.api.downloads.DownloadDestination
import ru.privatenull.pnlibrary.api.downloads.FileDownloads
import ru.privatenull.pnlibrary.api.plugin.PluginDependency
import ru.privatenull.pnlibrary.api.updates.ExternalPluginDependency
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import java.nio.file.Path

internal data class DependencyDownloadRequest(
    val request: FileDownloads,
    val requirements: Map<String, ExternalPluginDependency>,
)

internal class DependencyDownloadRequestFactory(private val pluginDirectory: Path) {
    fun create(
        dependencies: List<PluginDependency>,
        installedPlugins: Map<String, String>,
    ): DependencyDownloadRequest? {
        val downloadable = dependencies.mapNotNull { dependency ->
            val external = dependency.external ?: return@mapNotNull null
            val artifact = external.artifact ?: return@mapNotNull null
            if (!dependency.automaticDownload || isAlreadyCompatible(external, installedPlugins)) return@mapNotNull null
            Triple(dependency, external, artifact)
        }
        if (downloadable.isEmpty()) return null

        val request = FileDownloads.builder()
            .dataDirectory(pluginDirectory.resolve("update"))
            .also { builder ->
                downloadable.forEach { (dependency, external, artifact) ->
                    val key = dependencyKey(external.plugin)
                    builder.file(key) { file ->
                        file.url(artifact.uri.toString())
                            .required(dependency.required)
                            .automaticDownload(true)
                            .forceAutomaticDownload(dependency.forceAutomaticDownload)
                            .destination(DownloadDestination.DATA_FOLDER, safeFileName(external.plugin))
                        val expectedSize = artifact.size
                        val expectedHash = artifact.sha256
                        if (expectedSize != null && expectedHash != null) {
                            file.integrity(expectedSize, expectedHash)
                        }
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
