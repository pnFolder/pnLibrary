package org.pnfolder.pnlibrary.core.downloads.fetch

import org.pnfolder.pnlibrary.api.downloads.DirectDownloadSource
import org.pnfolder.pnlibrary.common.io.Digests
import org.pnfolder.pnlibrary.updates.engine.net.TrustedHttpClient

/**
 * Gets the bytes of one declared file from its address, then from its mirrors in order.
 *
 * With a SHA-256 declared, the first address whose file matches wins. Without one, the first address that answers wins:
 * the file is taken as it came, and nothing is compared with other addresses or remembered.
 */
internal class SourceFetcher(private val http: TrustedHttpClient) {
    fun fetch(source: DirectDownloadSource, limit: Int): ByteArray {
        val addresses = listOf(source.uri) + source.mirrors
        val expected = source.sha256
        val failures = mutableListOf<String>()
        var answered = false
        for (address in addresses) {
            val bytes = try {
                http.get(address, limit)
            } catch (error: Exception) {
                if (addresses.size == 1) throw error
                failures += "$address: ${error.message}"
                continue
            }
            if (expected == null || Digests.sha256(bytes).equals(expected, ignoreCase = true)) return bytes
            answered = true
            failures += "$address: SHA-256 файла не совпадает"
        }
        val message = "ни один источник не дал нужный файл (${failures.joinToString("; ")})"
        throw if (answered) IllegalArgumentException(message) else IllegalStateException(message)
    }
}
