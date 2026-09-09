package ru.privatenull.pnlibrary.core.upload

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import java.time.Instant
import java.util.EnumSet

/**
 * Local deletion-token ledger persisted as JSON.
 *
 * Deletion tokens are **never** included in user-visible report links.
 * The ledger file is owner-read-write only on POSIX systems.
 *
 * The ledger tolerates individual corrupted entries by logging and skipping
 * them — a partially damaged ledger does not prevent further recording.
 */
class UploadLedger(private val file: Path) {

    @Synchronized
    @Throws(IOException::class)
    fun record(receipt: UploadReceipt, deleteAfterDays: Int) {
        if (!receipt.canDelete() || deleteAfterDays <= 0) return
        val entries = readSafe()
        val deleteAt = receipt.createdEpochSeconds + deleteAfterDays * 86_400L
        entries.add(LedgerEntry(
            backend  = receipt.backend,
            link     = receipt.link.toString(),
            id       = receipt.id,
            token    = receipt.deleteToken,
            deleteAt = deleteAt,
        ))
        write(entries)
    }

    @Synchronized
    @Throws(IOException::class)
    fun cleanup(uploader: UploadProvider): Int {
        val entries   = readSafe()
        val remaining = mutableListOf<LedgerEntry>()
        var deleted   = 0
        val now       = Instant.now().epochSecond
        for (entry in entries) {
            if (entry.deleteAt > now) {
                remaining.add(entry)
                continue
            }
            try {
                val receipt = UploadReceipt(URI.create(entry.link), entry.id, entry.token, 0L, 0L, entry.backend)
                if (uploader.delete(receipt)) deleted++ else remaining.add(entry)
            } catch (_: Exception) {
                remaining.add(entry)
            }
        }
        write(remaining)
        return deleted
    }

    // ── I/O ──────────────────────────────────────────────────────────────────

    private fun readSafe(): MutableList<LedgerEntry> {
        if (!Files.exists(file)) return mutableListOf()
        if (Files.isSymbolicLink(file))          throw IOException("unsafe upload ledger: symlink")
        if (Files.size(file) > 1_048_576)        throw IOException("unsafe upload ledger: oversized")
        return try {
            val text = file.toFile().readText(Charsets.UTF_8)
            JSON.fromJson<List<LedgerEntry>?>(text, ENTRY_LIST_TYPE)?.toMutableList()
                ?: mutableListOf()
        } catch (e: RuntimeException) {
            // Corrupt ledger: treat as empty rather than failing completely
            mutableListOf()
        }
    }

    private fun write(entries: List<LedgerEntry>) {
        Files.createDirectories(file.parent)
        val tmp = file.resolveSibling("${file.fileName}.tmp")
        Files.write(
            tmp,
            JSON.toJson(entries).toByteArray(StandardCharsets.UTF_8),
            StandardOpenOption.CREATE,
            StandardOpenOption.TRUNCATE_EXISTING,
        )
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
        }
        try {
            Files.setPosixFilePermissions(
                file,
                EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            )
        } catch (_: UnsupportedOperationException) {
            // Not a POSIX filesystem (e.g. Windows) — skip silently
        } catch (_: IOException) { }
    }

    // ── Data model ───────────────────────────────────────────────────────────

    private data class LedgerEntry(
        val backend: String  = "",
        val link:    String  = "",
        val id:      String  = "",
        val token:   String  = "",
        val deleteAt: Long   = 0L,
    )

    companion object {
        private val JSON = Gson()
        private val ENTRY_LIST_TYPE = object : TypeToken<List<LedgerEntry>>() {}.type
    }
}
