package ru.privatenull.pnlibrary.core.diagnostics

import com.google.gson.Gson
import ru.privatenull.pnlibrary.core.security.EncryptedEnvelopeCodec
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.time.format.DateTimeFormatter

/** Encrypted rolling incident history. No password or private key is stored on the server. */
internal class PersistentDiagnosticHistory(
    private val directory: Path,
    private val codec: EncryptedEnvelopeCodec?,
    private val retentionDays: Int,
    private val maxBytes: Long,
) {
    private val gson = Gson()
    private val sessionStartedUtc = Instant.now().toString()
    private val session = "session-${DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(java.time.ZoneOffset.UTC).format(Instant.now())}.pndlog"

    @Synchronized
    fun save(logIncidents: List<Map<String, Any?>>, diagnosticEvents: Map<String, Any?>) {
        val encryption = codec ?: return
        Files.createDirectories(directory)
        val document = gson.toJson(linkedMapOf(
            "format" to "pnlibrary-incident-history",
            "version" to 1,
            "sessionStartedUtc" to sessionStartedUtc,
            "updatedUtc" to Instant.now().toString(),
            "logIncidents" to logIncidents,
            "pluginEvents" to diagnosticEvents,
        ))
        val encrypted = encryption.encryptBinary(document.toByteArray(StandardCharsets.UTF_8), "incident-history")
        val target = directory.resolve(session)
        val temporary = Files.createTempFile(directory, session, ".tmp")
        try {
            Files.write(temporary, encrypted)
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
        rotate()
    }

    fun files(): List<Pair<String, ByteArray>> {
        if (!Files.isDirectory(directory)) return emptyList()
        return Files.list(directory).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".pndlog") }
                .sorted().map { it.fileName.toString() to Files.readAllBytes(it) }.toList()
        }
    }

    private fun rotate() {
        val newestFirst = Files.list(directory).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".pndlog") }
                .sorted { a, b -> Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a)) }.toList()
        }
        val oldestAllowed = System.currentTimeMillis() - retentionDays * 86_400_000L
        val retained = newestFirst.filterIndexed { index, file ->
            val expired = runCatching { Files.getLastModifiedTime(file).toMillis() < oldestAllowed }.getOrDefault(false)
            if (expired || index >= MAX_FILES) {
                Files.deleteIfExists(file)
                false
            } else {
                true
            }
        }

        var total = retained.sumOf { runCatching { Files.size(it) }.getOrDefault(0L) }
        retained.asReversed().forEach { file ->
            if (total > maxBytes) {
                total -= runCatching { Files.size(file) }.getOrDefault(0L)
                Files.deleteIfExists(file)
            }
        }
    }

    private companion object {
        const val MAX_FILES = 256
    }
}
