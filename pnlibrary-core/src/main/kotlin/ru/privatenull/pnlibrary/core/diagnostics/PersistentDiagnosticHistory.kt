package ru.privatenull.pnlibrary.core.diagnostics

import com.google.gson.Gson
import ru.privatenull.pnlibrary.core.security.EncryptedEnvelopeCodec
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Persists a bounded, encrypted rolling history of diagnostic incidents.
 *
 * No private key or plaintext history is stored on disk. Each runtime instance owns
 * a unique session file, rewritten atomically as incidents change. Rotation applies
 * age, file-count, and aggregate-byte limits.
 *
 * @param directory dedicated history directory
 * @param codec public-key envelope codec; `null` disables persistence
 * @param retentionDays maximum file age in whole days
 * @param maxBytes aggregate encrypted history budget
 * @param clock time source used for timestamps and retention
 */
internal class PersistentDiagnosticHistory(
    private val directory: Path,
    private val codec: EncryptedEnvelopeCodec?,
    private val retentionDays: Int,
    private val maxBytes: Long,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val gson = Gson()
    private val sessionStarted = clock.instant()
    private val sessionStartedUtc = sessionStarted.toString()
    private val session = "session-${SESSION_TIME_FORMAT.format(sessionStarted)}-${UUID.randomUUID()}.pndlog"

    init {
        require(retentionDays > 0) { "retentionDays must be positive" }
        require(maxBytes > 0) { "maxBytes must be positive" }
    }

    /** Encrypts and atomically replaces the current runtime session snapshot. */
    @Synchronized
    fun save(logIncidents: List<Map<String, Any?>>, diagnosticEvents: Map<String, Any?>) {
        val encryption = codec ?: return
        Files.createDirectories(directory)
        val document = gson.toJson(linkedMapOf(
            "format" to "pnlibrary-incident-history",
            "version" to 1,
            "sessionStartedUtc" to sessionStartedUtc,
            "updatedUtc" to clock.instant().toString(),
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

    /**
     * Returns retained encrypted files without exceeding [maxBytes].
     *
     * Files changed externally to exceed the per-directory budget are skipped
     * before allocation.
     */
    fun files(): List<Pair<String, ByteArray>> {
        if (!Files.isDirectory(directory)) return emptyList()
        val candidates = Files.list(directory).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".pndlog") }
                .sorted().toList()
        }
        val result = mutableListOf<Pair<String, ByteArray>>()
        var remainingBytes = maxBytes
        candidates.forEach { file ->
            val size = fileSize(file) ?: return@forEach
            if (size <= 0 || size > remainingBytes) return@forEach
            result += file.fileName.toString() to Files.readAllBytes(file)
            remainingBytes -= size
        }
        return result
    }

    private fun rotate() {
        val newestFirst = Files.list(directory).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".pndlog") }
                .sorted { a, b -> Files.getLastModifiedTime(b).compareTo(Files.getLastModifiedTime(a)) }.toList()
        }
        val oldestAllowed = clock.millis() - retentionDays * MILLIS_PER_DAY
        val retained = newestFirst.filterIndexed { index, file ->
            val expired = lastModifiedMillis(file)?.let { it < oldestAllowed } ?: false
            if (expired || index >= MAX_FILES) {
                Files.deleteIfExists(file)
                false
            } else {
                true
            }
        }

        var total = retained.sumOf { fileSize(it) ?: 0L }
        retained.asReversed().forEach { file ->
            if (total > maxBytes) {
                total -= fileSize(file) ?: 0L
                Files.deleteIfExists(file)
            }
        }
    }

    private fun fileSize(file: Path): Long? = try {
        Files.size(file)
    } catch (_: Exception) {
        null
    }

    private fun lastModifiedMillis(file: Path): Long? = try {
        Files.getLastModifiedTime(file).toMillis()
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val MAX_FILES = 256
        const val MILLIS_PER_DAY = 86_400_000L
        val SESSION_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
            .withZone(java.time.ZoneOffset.UTC)
    }
}
