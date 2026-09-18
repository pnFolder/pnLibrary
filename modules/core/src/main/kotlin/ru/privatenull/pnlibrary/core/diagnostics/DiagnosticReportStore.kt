package ru.privatenull.pnlibrary.core.diagnostics

import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

/**
 * Persists generated diagnostic reports and enforces local retention.
 *
 * Only files created in the `report-*` namespace with a supported report
 * extension participate in cleanup. Unrelated files in the same directory are
 * never removed.
 *
 * @param directory directory that owns local report artifacts
 */
internal class DiagnosticReportStore(
    private val directory: Path,
) {
    /**
     * Atomically stores [payload], then retains at most [keepCount] reports.
     *
     * @param encrypted selects `.pnsupport`; plaintext archives use `.zip`
     * @return final report path
     */
    fun save(payload: ByteArray, encrypted: Boolean, keepCount: Int): Path {
        require(keepCount > 0) { "keepCount must be positive" }
        Files.createDirectories(directory)
        val extension = if (encrypted) ENCRYPTED_EXTENSION else ARCHIVE_EXTENSION
        val target = Files.createTempFile(directory, REPORT_PREFIX, extension)
        val staging = target.resolveSibling("${target.fileName}.tmp")
        try {
            Files.write(staging, payload)
            moveIntoPlace(staging, target)
        } catch (exception: Exception) {
            Files.deleteIfExists(target)
            throw exception
        } finally {
            Files.deleteIfExists(staging)
        }
        removeExpiredReports(keepCount)
        return target
    }

    private fun removeExpiredReports(keepCount: Int) {
        val reports = Files.list(directory).use { stream ->
            stream.filter(::isOwnedReport)
                .sorted { left, right ->
                    Files.getLastModifiedTime(right).compareTo(Files.getLastModifiedTime(left))
                }
                .toList()
        }
        reports.drop(keepCount).forEach { report ->
            try {
                Files.deleteIfExists(report)
            } catch (_: Exception) {
                // Retention is best-effort; a locked old report must not lose the new one.
            }
        }
    }

    private fun isOwnedReport(path: Path): Boolean {
        if (!Files.isRegularFile(path)) return false
        val name = path.fileName.toString()
        return name.startsWith(REPORT_PREFIX) &&
            (name.endsWith(ENCRYPTED_EXTENSION) || name.endsWith(ARCHIVE_EXTENSION))
    }

    private fun moveIntoPlace(source: Path, target: Path) {
        try {
            Files.move(
                source,
                target,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        const val REPORT_PREFIX = "report-"
        const val ENCRYPTED_EXTENSION = ".pnsupport"
        const val ARCHIVE_EXTENSION = ".zip"
    }
}
