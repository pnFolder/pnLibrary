package ru.privatenull.pnlibrary.core.diagnostics

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Builds the stable, human-readable file layout inside a `.pndebug` payload. */
internal class DiagnosticArchiveBuilder {
    private val gson: Gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val entries = linkedMapOf<String, ByteArray>()

    fun json(path: String, value: Any?) = bytes(path, gson.toJson(value).toByteArray(StandardCharsets.UTF_8))
    fun text(path: String, value: String) = bytes(path, value.toByteArray(StandardCharsets.UTF_8))

    fun bytes(path: String, value: ByteArray) {
        val safe = path.replace('\\', '/').trimStart('/').also {
            require(it.isNotBlank() && ".." !in it.split('/')) { "Invalid diagnostic archive path: $path" }
        }
        entries[safe] = value
    }

    fun build(): ByteArray {
        val checksums = entries.mapValues { (_, bytes) -> sha256(bytes) }
        json("checksums.json", checksums)
        val output = ByteArrayOutputStream()
        ZipOutputStream(output, StandardCharsets.UTF_8).use { zip ->
            entries.forEach { (path, content) ->
                zip.putNextEntry(ZipEntry("pn-diagnostic/$path").apply { time = 0L })
                zip.write(content)
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }
}
