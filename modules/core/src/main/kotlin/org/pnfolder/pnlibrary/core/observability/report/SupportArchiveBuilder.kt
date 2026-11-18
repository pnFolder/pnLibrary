package org.pnfolder.pnlibrary.core.observability.report

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.pnfolder.pnlibrary.common.io.Digests
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// Content is copied at insertion and build() does not mutate state, so repeated builds are equivalent.
internal class SupportArchiveBuilder(
    private val maximumUncompressedBytes: Long = Long.MAX_VALUE,
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val entries = linkedMapOf<String, ByteArray>()
    private var payloadBytes = 0L

    init {
        require(maximumUncompressedBytes > 0) { "maximumUncompressedBytes must be positive" }
    }

    fun json(path: String, value: Any?) = bytes(path, gson.toJson(value).toByteArray(StandardCharsets.UTF_8))

    fun text(path: String, value: String) = bytes(path, value.toByteArray(StandardCharsets.UTF_8))

    fun bytes(path: String, value: ByteArray) {
        val safe = path.replace('\\', '/').trimStart('/').also {
            require(it.isNotBlank() && ".." !in it.split('/')) { "Invalid diagnostic archive path: $path" }
        }
        val previousSize = entries[safe]?.size ?: 0
        val nextSize = payloadBytes - previousSize + value.size
        require(nextSize <= maximumUncompressedBytes) {
            "Diagnostic payload exceeds configured limit ($nextSize > $maximumUncompressedBytes bytes)"
        }
        entries[safe] = value.copyOf()
        payloadBytes = nextSize
    }

    fun build(): ByteArray {
        val checksums = entries.mapValues { (_, bytes) -> Digests.sha256(bytes) }
        val archiveEntries = entries + mapOf(
            CHECKSUM_PATH to gson.toJson(checksums).toByteArray(StandardCharsets.UTF_8),
        )
        val output = ByteArrayOutputStream()
        ZipOutputStream(output, StandardCharsets.UTF_8).use { zip ->
            archiveEntries.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry("pn-diagnostic/$path").apply { time = 0L })
                zip.write(content)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private companion object {
        const val CHECKSUM_PATH = "checksums.json"
    }
}
