package org.pnfolder.pnlibrary.core.journal

import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

/** Names and listing of segment files `<level>-<yyyy-MM-dd>[-segment].pnj` in one journal folder. */
internal class JournalFiles(private val directory: Path) {
    /** Directory listings made so far; each one costs a system call per file, so callers keep it rare. */
    val listings = AtomicInteger()

    /** Every segment file, sorted by name. */
    fun all(): List<Path> {
        if (!Files.isDirectory(directory)) return emptyList()
        listings.incrementAndGet()
        return Files.list(directory).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".pnj") }
                .sorted()
                .collect(java.util.stream.Collectors.toList())
        }
    }

    fun of(level: JournalLevel, files: List<Path>): List<Path> {
        val prefix = "${level.fileGroup}-"
        return files.filter { it.fileName.toString().startsWith(prefix) }
    }

    fun levelOf(file: Path): JournalLevel? =
        JournalLevel.values().firstOrNull { file.fileName.toString().startsWith("${it.fileGroup}-") }

    fun pathOf(level: JournalLevel, date: LocalDate, segment: Int): Path =
        directory.resolve("${level.fileGroup}-$date${if (segment == 0) "" else "-$segment"}.pnj")

    fun dateOf(file: Path): LocalDate? =
        runCatchingNonFatal { LocalDate.parse(file.fileName.toString().substringAfter('-').take(10)) }
            .getOrNull()

    fun segmentOf(file: Path): Int = file.fileName.toString().substringAfter('-').drop(10)
        .removePrefix("-").removeSuffix(".pnj").toIntOrNull() ?: 0
}
