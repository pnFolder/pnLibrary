package org.pnfolder.pnlibrary.core.downloads.fetch

import org.pnfolder.pnlibrary.api.downloads.DirectDownloadSource
import org.pnfolder.pnlibrary.common.io.Digests
import org.pnfolder.pnlibrary.core.downloads.settings.DownloadPins
import org.pnfolder.pnlibrary.core.updates.engine.net.TrustedHttpClient
import java.net.URI

/**
 * Gets the bytes of one declared file from its address and mirrors.
 *
 * With a checksum, the first address whose file matches wins. Without one, every reachable address is asked and the
 * copies must be identical, so a file replaced at one place is caught; the declared size is checked afterwards.
 * A lone copy is held to the checksum remembered by [DownloadPins].
 */
internal class SourceFetcher(private val http: TrustedHttpClient, private val pins: DownloadPins) {
    fun fetch(source: DirectDownloadSource, limit: Int): ByteArray {
        val addresses = listOf(source.uri) + source.mirrors
        val expected = source.sha256
        return if (expected != null) firstMatching(addresses, expected, limit) else agreed(source, addresses, limit)
    }

    private fun firstMatching(addresses: List<URI>, sha256: String, limit: Int): ByteArray {
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
            if (Digests.sha256(bytes).equals(sha256, ignoreCase = true)) return bytes
            answered = true
            failures += "$address: SHA-256 файла не совпадает"
        }
        val message = "ни один источник не дал нужный файл (${failures.joinToString("; ")})"
        throw if (answered) IllegalArgumentException(message) else IllegalStateException(message)
    }

    private fun agreed(source: DirectDownloadSource, addresses: List<URI>, limit: Int): ByteArray {
        val copies = linkedMapOf<URI, ByteArray>()
        val failures = mutableListOf<String>()
        for (address in addresses) {
            try {
                copies[address] = http.get(address, limit)
            } catch (error: Exception) {
                if (addresses.size == 1) throw error
                failures += "$address: ${error.message}"
            }
        }
        if (copies.isEmpty()) throw IllegalStateException("ни один источник не ответил (${failures.joinToString("; ")})")
        val hashes = copies.mapValues { Digests.sha256(it.value) }
        require(hashes.values.toSet().size == 1) {
            "источники отдают разные файлы: ${hashes.entries.joinToString("; ") { "${it.key} → ${it.value.take(SHORT_HASH)}" }}"
        }
        val sha256 = hashes.values.first()
        pins.check(source.uri, sha256)
        pins.remember(source.uri, sha256)
        return copies.values.first()
    }

    private companion object {
        const val SHORT_HASH = 12
    }
}
