package ru.privatenull.pnlibrary.core.downloads

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import ru.privatenull.pnlibrary.api.downloads.DownloadDestination
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

internal data class DownloadConfiguration(
    val enabled: Boolean = true,
    val automatic: Boolean = false,
    val allowedHosts: Set<String> = setOf("github.com", "objects.githubusercontent.com"),
    val destinations: Set<DownloadDestination> = setOf(
        DownloadDestination.DATA_FOLDER, DownloadDestination.CACHE,
    ),
) {
    companion object {
        fun load(path: Path): DownloadConfiguration {
            Files.createDirectories(path.toAbsolutePath().parent)
            if (!Files.exists(path)) Files.writeString(path, DEFAULT, StandardCharsets.UTF_8)
            val root = runCatching {
                @Suppress("UNCHECKED_CAST")
                (Yaml(SafeConstructor(LoaderOptions())).load<Any?>(Files.readString(path)) as? Map<String, Any?>)
                    ?.get("downloads") as? Map<String, Any?>
            }.getOrNull() ?: return DownloadConfiguration()
            val defaults = DownloadConfiguration()
            fun flag(name: String, fallback: Boolean) = root[name] as? Boolean ?: fallback
            val hosts = (root["allowed-hosts"] as? List<*>)?.filterIsInstance<String>()
                ?.map(String::lowercase)?.filter { it.matches(Regex("[A-Za-z0-9.-]+")) }?.toSet()
                ?.takeIf(Set<String>::isNotEmpty) ?: defaults.allowedHosts
            val destinationMap = root["destinations"] as? Map<*, *>
            val destinations = DownloadDestination.entries.filterTo(linkedSetOf()) { destination ->
                val key = destination.name.lowercase().replace('_', '-')
                destinationMap?.get(key) as? Boolean ?: true
            }
            return DownloadConfiguration(flag("enabled", true), flag("automatic", false), hosts, destinations)
        }

        private const val DEFAULT = """downloads:
  enabled: true
  automatic: false
  allowed-hosts:
    - github.com
    - objects.githubusercontent.com
  destinations:
    data-folder: true
    cache: true
"""
    }
}
