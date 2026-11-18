package org.pnfolder.pnlibrary.core.upload

import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import java.io.IOException
import java.net.URI
import java.nio.file.Path
import java.time.Clock

// Receipts without a delete token or deleteAfterDays <= 0 are intentionally skipped.
// Network failures keep the entry for the next cleanup attempt.
class UploadLedger @JvmOverloads constructor(
    file: Path,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val persistence = UploadLedgerPersistence(file, clock)

    @Synchronized
    @Throws(IOException::class)
    fun record(receipt: UploadReceipt, deleteAfterDays: Int) {
        if (!receipt.canDelete() || deleteAfterDays <= 0) return
        val entries = persistence.read()
        val createdAt = receipt.createdEpochSeconds.takeIf { it > 0 }
            ?: clock.instant().epochSecond
        val entry = UploadLedgerEntry(
            backend = receipt.backend,
            link = receipt.link.toString(),
            id = receipt.id,
            token = receipt.deleteToken,
            deleteAt = createdAt + deleteAfterDays * SECONDS_PER_DAY,
        )
        entries.removeAll { it.backend == entry.backend && it.id == entry.id }
        entries += entry
        persistence.write(entries)
    }

    @Synchronized
    @Throws(IOException::class)
    fun cleanup(uploader: UploadProvider): Int {
        val remaining = mutableListOf<UploadLedgerEntry>()
        var deleted = 0
        val now = clock.instant().epochSecond
        persistence.read().forEach { entry ->
            if (entry.deleteAt > now) {
                remaining += entry
                return@forEach
            }
            if (tryDelete(uploader, entry)) deleted++ else remaining += entry
        }
        persistence.write(remaining)
        return deleted
    }

    @Synchronized
    fun diagnosticSnapshot(): Map<String, Any?> {
        // Diagnostics must render even with an unreadable ledger; cleanup() surfaces the I/O error.
        val entries = runCatchingNonFatal { persistence.read() }.getOrDefault(emptyList())
        val now = clock.instant().epochSecond
        return linkedMapOf(
            "pendingCount" to entries.size,
            "dueCount" to entries.count { it.deleteAt <= now },
            "byBackend" to entries.groupingBy { it.backend }.eachCount(),
            "nextDeletionEpochSeconds" to entries.minOfOrNull { it.deleteAt },
        )
    }

    private fun tryDelete(uploader: UploadProvider, entry: UploadLedgerEntry): Boolean = try {
        uploader.delete(
            UploadReceipt(
                link = URI.create(entry.link),
                id = entry.id,
                deleteToken = entry.token,
                createdEpochSeconds = 0,
                expiresEpochSeconds = 0,
                backend = entry.backend,
            ),
        )
    } catch (_: Exception) {
        // A failed deletion keeps the entry in the ledger, so it is retried on the next cleanup.
        false
    }

    private companion object {
        const val SECONDS_PER_DAY = 86_400L
    }
}
