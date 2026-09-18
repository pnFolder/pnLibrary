package ru.privatenull.pnlibrary.core.upload

import java.io.IOException
import java.net.URI
import java.nio.file.Path
import java.time.Clock

/**
 * Schedules and executes deletion of remotely uploaded diagnostic reports.
 *
 * Receipts without deletion tokens and policies with `deleteAfterDays <= 0` are
 * intentionally ignored. Network failures retain the entry for a later cleanup
 * attempt. JSON and filesystem concerns are delegated to [UploadLedgerPersistence].
 *
 * @param file local JSON ledger path; deletion tokens never enter report archives
 * @param clock time source used for deterministic scheduling and cleanup
 */
class UploadLedger @JvmOverloads constructor(
    file: Path,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val persistence = UploadLedgerPersistence(file, clock)

    /**
     * Records a future deletion for [receipt].
     *
     * If the backend did not provide a creation timestamp, the current [clock]
     * instant becomes the scheduling baseline.
     *
     * @param deleteAfterDays whole days to retain the remote report
     * @throws IOException if ledger persistence fails
     */
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

    /**
     * Attempts every due remote deletion and persists unfinished entries.
     *
     * Entries scheduled in the future and entries whose provider rejects or fails
     * deletion remain in the ledger.
     *
     * @return number of entries successfully removed remotely
     * @throws IOException if reading or rewriting the ledger fails
     */
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
        false
    }

    private companion object {
        const val SECONDS_PER_DAY = 86_400L
    }
}
