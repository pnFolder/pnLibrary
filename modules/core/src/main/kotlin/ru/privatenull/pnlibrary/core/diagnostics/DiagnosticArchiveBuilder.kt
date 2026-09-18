package ru.privatenull.pnlibrary.core.diagnostics

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Builds the deterministic ZIP layout encrypted inside a `.pnsupport` container.
 *
 * Entry paths are relative and traversal-safe. Content is copied at insertion,
 * the uncompressed byte budget is enforced before ZIP compression, and [build]
 * does not mutate builder state, so repeated builds produce equivalent archives.
 *
 * @param maximumUncompressedBytes total payload budget before ZIP compression
 */
internal class DiagnosticArchiveBuilder(
    private val maximumUncompressedBytes: Long = Long.MAX_VALUE,
) {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val entries = linkedMapOf<String, ByteArray>()
    private var payloadBytes = 0L

    init {
        require(maximumUncompressedBytes > 0) { "maximumUncompressedBytes must be positive" }
    }

    /** Serializes [value] as pretty JSON at [path]. */
    fun json(path: String, value: Any?) = bytes(path, gson.toJson(value).toByteArray(StandardCharsets.UTF_8))

    /** Stores UTF-8 [value] at [path]. */
    fun text(path: String, value: String) = bytes(path, value.toByteArray(StandardCharsets.UTF_8))

    /**
     * Adds or replaces one archive entry using a defensive copy of [value].
     *
     * @throws IllegalArgumentException for an empty/traversing path or if the new
     * aggregate payload exceeds the configured uncompressed byte budget
     */
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

    /** Creates a ZIP containing all entries and an SHA-256 checksum manifest. */
    fun build(): ByteArray {
        val checksums = entries.mapValues { (_, bytes) -> sha256(bytes) }
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

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val CHECKSUM_PATH = "checksums.json"
    }
}
