package ru.privatenull.pnlibrary.core.downloads

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

internal data class PreparedDownload(
    val staging: Path,
    val target: Path,
)

/** Publishes a prepared download batch as one recoverable filesystem transaction. */
internal class AtomicDownloadPublisher(
    private val libraryData: Path,
) {
    fun publish(downloads: List<PreparedDownload>) {
        val transactionDirectory = libraryData.resolve("downloads/transactions/${UUID.randomUUID()}")
        val backups = mutableListOf<Backup>()
        val publishedTargets = mutableListOf<Path>()

        try {
            downloads.forEachIndexed { index, download ->
                Files.createDirectories(download.target.parent)
                backupExistingTarget(download.target, index, transactionDirectory)?.let(backups::add)
                move(download.staging, download.target)
                publishedTargets.add(download.target)
            }
        } catch (error: Throwable) {
            rollback(publishedTargets, backups)
            throw error
        } finally {
            cleanTemporaryFiles(downloads, backups, transactionDirectory)
        }
    }

    private fun backupExistingTarget(
        target: Path,
        index: Int,
        transactionDirectory: Path,
    ): Backup? {
        if (!Files.exists(target)) return null

        val backup = transactionDirectory.resolve("$index-${target.fileName}")
        Files.createDirectories(backup.parent)
        Files.move(target, backup, StandardCopyOption.REPLACE_EXISTING)
        return Backup(target, backup)
    }

    private fun rollback(
        publishedTargets: List<Path>,
        backups: List<Backup>,
    ) {
        publishedTargets.asReversed().forEach(Files::deleteIfExists)
        backups.asReversed().forEach { backup ->
            if (Files.exists(backup.file)) move(backup.file, backup.originalTarget)
        }
    }

    private fun cleanTemporaryFiles(
        downloads: List<PreparedDownload>,
        backups: List<Backup>,
        transactionDirectory: Path,
    ) {
        downloads.forEach { Files.deleteIfExists(it.staging) }
        backups.forEach { Files.deleteIfExists(it.file) }
        runCatching { Files.deleteIfExists(transactionDirectory) }
    }

    private fun move(source: Path, target: Path) {
        try {
            Files.move(
                source,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private data class Backup(
        val originalTarget: Path,
        val file: Path,
    )
}
