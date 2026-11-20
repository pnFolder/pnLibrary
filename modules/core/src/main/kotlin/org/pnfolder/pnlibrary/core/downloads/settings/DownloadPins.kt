package org.pnfolder.pnlibrary.core.downloads.settings

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * Remembers the SHA-256 of every file downloaded without a declared checksum. The same address must keep giving the
 * same file: a different one means the file was replaced, so it waits for the administrator's decision.
 */
internal class DownloadPins(libraryData: Path) {
    private val file: Path = libraryData.resolve("downloads/pins.state")

    @Synchronized
    fun check(uri: URI, sha256: String) {
        val known = read()[uri.toString()] ?: return
        require(known.equals(sha256, ignoreCase = true)) {
            "файл по адресу $uri изменился с прошлой загрузки; проверьте его и подтвердите командой установки"
        }
    }

    @Synchronized
    fun remember(uri: URI, sha256: String) {
        write(read() + (uri.toString() to sha256.lowercase()))
    }

    /** Lets the next download of these addresses replace the remembered file. */
    @Synchronized
    fun forget(uris: Collection<URI>) {
        val gone = uris.map(URI::toString).toSet()
        write(read().filterKeys { it !in gone })
    }

    private fun read(): Map<String, String> = if (!Files.exists(file)) emptyMap() else Files.readAllLines(file)
        .mapNotNull { line -> line.trim().split(' ', limit = 2).takeIf { it.size == 2 }?.let { it[1] to it[0] } }
        .toMap()

    private fun write(pins: Map<String, String>) {
        Files.createDirectories(file.parent)
        Files.write(file, pins.map { (uri, sha) -> "$sha $uri" }.sorted())
    }
}
