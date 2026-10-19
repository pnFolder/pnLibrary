package org.pnfolder.pnlibrary.core.downloads

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.pnfolder.pnlibrary.api.downloads.DownloadDestination
import org.pnfolder.pnlibrary.core.io.TextFiles
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/** Administrator policy from `downloads.yml`: whether downloads run, start automatically, and which destinations are allowed. */
internal data class DownloadConfiguration(
    val enabled: Boolean = true,
    val automatic: Boolean = false,
    val destinations: Set<DownloadDestination> = setOf(
        DownloadDestination.DATA_FOLDER, DownloadDestination.CACHE,
    ),
) {
    companion object {
        fun load(path: Path): DownloadConfiguration {
            Files.createDirectories(path.toAbsolutePath().parent)
            if (!Files.exists(path)) TextFiles.write(path, DEFAULT, StandardCharsets.UTF_8)
            val root = runCatching {
                @Suppress("UNCHECKED_CAST")
                (Yaml(SafeConstructor(LoaderOptions())).load<Any?>(TextFiles.read(path)) as? Map<String, Any?>)
                    ?.get("downloads") as? Map<String, Any?>
            }.getOrNull() ?: return DownloadConfiguration()
            val defaults = DownloadConfiguration()
            fun flag(name: String, fallback: Boolean) = root[name] as? Boolean ?: fallback
            val destinationMap = root["destinations"] as? Map<*, *>
            val destinations = DownloadDestination.entries.filterTo(linkedSetOf()) { destination ->
                val key = destination.name.lowercase().replace('_', '-')
                destinationMap?.get(key) as? Boolean ?: true
            }
            return DownloadConfiguration(flag("enabled", true), flag("automatic", false), destinations = destinations)
        }

        private const val DEFAULT = """downloads:
  enabled: true
  automatic: false
  # Любые адреса разрешены. Ограничения по хостам отсутствуют.
  destinations:
    data-folder: true
    cache: true
"""
    }
}
