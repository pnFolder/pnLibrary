package org.pnfolder.pnlibrary.core.downloads.settings

import java.nio.file.Files
import java.nio.file.Path

/**
 * What the administrator chose in the game menu or with `/pn deps auto`. It lives in the data folder, not in
 * `downloads.yml`, and wins over the file: a choice made in game is never undone by an old config.
 */
internal class DownloadPreferences(libraryData: Path, private val fallback: () -> Boolean) {
    private val file: Path = libraryData.resolve("downloads/preferences.state")

    @Synchronized
    fun automatic(): Boolean = stored() ?: fallback()

    @Synchronized
    fun setAutomatic(value: Boolean) {
        Files.createDirectories(file.parent)
        Files.write(file, listOf("$KEY=$value"))
    }

    private fun stored(): Boolean? = if (!Files.exists(file)) null else Files.readAllLines(file)
        .firstOrNull { it.startsWith("$KEY=") }
        ?.substringAfter('=')?.trim()?.toBooleanStrictOrNull()

    private companion object {
        const val KEY = "automatic"
    }
}
