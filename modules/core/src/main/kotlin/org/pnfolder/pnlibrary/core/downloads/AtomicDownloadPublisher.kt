package org.pnfolder.pnlibrary.core.downloads

import org.pnfolder.pnlibrary.common.io.AtomicFiles
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.core.lifecycle.ResourceCleanup
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.UUID

/** A verified file waiting in [staging] to replace [target]. */
internal data class PreparedDownload(
    val staging: Path,
    val target: Path,
)

/** Publishes a prepared download batch as one recoverable filesystem transaction. */
internal class AtomicDownloadPublisher(
    private val libraryData: Path,
    private val moveFile: (Path, Path) -> Unit = { source, target -> AtomicFiles.move(source, target) },
) {
    fun publish(downloads: List<PreparedDownload>) {
        val transactionDirectory = libraryData.resolve("downloads/transactions/${UUID.randomUUID()}")
        val backups = mutableListOf<Backup>()
        val publishedTargets = mutableListOf<Path>()

        try {
            downloads.forEachIndexed { index, download ->
                Files.createDirectories(download.target.parent)
                backupExistingTarget(download.target, index, transactionDirectory)?.let(backups::add)
                moveFile(download.staging, download.target)
                publishedTargets.add(download.target)
            }
        } catch (error: Throwable) {
            rollback(error, publishedTargets, backups)
            // A backup that could not be restored is the only remaining copy of the original file.
            ResourceCleanup.suppressInto(error, { cleanTemporaryFiles(downloads, emptyList(), transactionDirectory) })
            throw error
        }
        cleanTemporaryFiles(downloads, backups, transactionDirectory)
    }

    private fun backupExistingTarget(
        target: Path,
        index: Int,
        transactionDirectory: Path,
    ): Backup? {
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return null
        require(Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) { "Download target must be a regular file: $target" }

        val backup = transactionDirectory.resolve("$index-${target.fileName}")
        Files.createDirectories(backup.parent)
        Files.move(target, backup, StandardCopyOption.REPLACE_EXISTING)
        return Backup(target, backup)
    }

    private fun rollback(
        primary: Throwable,
        publishedTargets: List<Path>,
        backups: List<Backup>,
    ) {
        ResourceCleanup.suppressInto(
            primary,
            *publishedTargets.asReversed().map { target -> ({ Files.deleteIfExists(target); Unit }) }.toTypedArray(),
            *backups.asReversed().map { backup ->
                { if (Files.exists(backup.file)) moveFile(backup.file, backup.originalTarget) }
            }.toTypedArray(),
        )
    }

    private fun cleanTemporaryFiles(
        downloads: List<PreparedDownload>,
        backups: List<Backup>,
        transactionDirectory: Path,
    ) {
        ResourceCleanup.closeAll(
            *downloads.map { download -> ({ Files.deleteIfExists(download.staging); Unit }) }.toTypedArray(),
            *backups.map { backup -> ({ Files.deleteIfExists(backup.file); Unit }) }.toTypedArray(),
            { runCatchingNonFatal { Files.deleteIfExists(transactionDirectory) }; Unit },
        )
    }

    private data class Backup(
        val originalTarget: Path,
        val file: Path,
    )
}
