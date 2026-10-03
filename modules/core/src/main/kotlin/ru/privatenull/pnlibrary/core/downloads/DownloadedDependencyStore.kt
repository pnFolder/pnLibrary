package ru.privatenull.pnlibrary.core.downloads

import java.nio.file.Files
import java.nio.file.Path

internal data class DownloadedDependency(val name: String, val version: String)

internal class DownloadedDependencyStore(libraryData: Path) {
    internal val marker: Path = libraryData.resolve("downloads/dependencies.state")

    fun record(dependencies: Collection<String>) {
        val recorded = readNames().toMutableList()
        dependencies.forEach { dependency ->
            if (recorded.none { it.equals(dependency, ignoreCase = true) }) recorded += dependency
        }
        Files.createDirectories(marker.parent)
        Files.write(marker, recorded.sortedWith(String.CASE_INSENSITIVE_ORDER))
    }

    fun consumeInstalled(installed: Map<String, String>): List<DownloadedDependency> {
        val recorded = readNames()
        val confirmed = recorded.mapNotNull { expected ->
            installed.entries.firstOrNull { it.key.equals(expected, ignoreCase = true) }
                ?.let { DownloadedDependency(it.key, it.value) }
        }
        if (confirmed.isEmpty()) return emptyList()

        val remaining = recorded.filterNot { expected ->
            confirmed.any { it.name.equals(expected, ignoreCase = true) }
        }
        if (remaining.isEmpty()) Files.deleteIfExists(marker) else Files.write(marker, remaining)
        return confirmed
    }

    private fun readNames(): List<String> = if (Files.exists(marker)) {
        Files.readAllLines(marker).map(String::trim).filter(String::isNotEmpty)
    } else {
        emptyList()
    }
}
