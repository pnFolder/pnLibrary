package org.pnfolder.pnlibrary.core.journal

import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.PriorityQueue

/**
 * Durable, local journal of everything the library and its plugins report.
 *
 * Records are grouped by event date into bounded segments named `<level>-<yyyy-MM-dd>[-segment].pnj`.
 * Legacy daily files remain readable. Reads keep only the selected newest records in memory and
 * scan at most [maximumReadBytes]; summary exposes an exhausted budget. Retention also caps disk use.
 * Every append writes a whole block to the end of today's file. ERROR records are forced to disk
 * before [append] returns, so a crash right after an error cannot lose it.
 *
 * Retention is per level: each level's files are removed once they are older than that level's limit,
 * so an error is kept longer than a routine info line.
 *
 * @param directory journal folder, created on first use
 * @param retention maximum age of files per level
 * @param clock source of the current date for file names and pruning
 */
internal class JournalStore(
    private val directory: Path,
    private val retention: Map<JournalLevel, Duration>,
    private val clock: Clock = Clock.systemUTC(),
    private val maximumSegmentBytes: Long = 8L * 1024 * 1024,
    private val maximumDiskBytes: Long = 256L * 1024 * 1024,
    private val maximumReadBytes: Long = 64L * 1024 * 1024,
) {
    init {
        require(JournalLevel.values().all { it in retention }) { "Every journal level needs a retention period" }
        require(maximumSegmentBytes > 0 && maximumDiskBytes >= maximumSegmentBytes && maximumReadBytes > 0)
    }

    private var lastPrunedOn: LocalDate? = null

    /**
     * Writes [record] as one block to today's file of its level.
     *
     * Old files are pruned once per day here, so a server that runs for weeks without a restart still
     * honours the retention limits.
     */
    @Synchronized
    fun append(record: JournalRecord) {
        Files.createDirectories(directory)
        val today = today()
        if (lastPrunedOn != today) {
            lastPrunedOn = today
            runCatching { prune() }
        }
        val block = JournalBlockCodec.encode(listOf(record))
        require(block.size <= maximumDiskBytes) { "Journal block exceeds disk budget" }
        val date = Instant.ofEpochMilli(record.timeMillis).atZone(ZoneOffset.UTC).toLocalDate()
        val existing = filesOf(record.level).filter { dateOf(it) == date }.maxByOrNull(::segmentOf)
        val target = if (existing != null && Files.size(existing) + block.size <= maximumSegmentBytes) existing else {
            val segment = existing?.let { segmentOf(it) + 1 } ?: 0
            directory.resolve("${record.level.fileGroup}-$date${if (segment == 0) "" else "-$segment"}.pnj")
        }
        FileChannel.open(target, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
            StandardOpenOption.APPEND).use { channel ->
            val bytes = java.nio.ByteBuffer.wrap(block)
            while (bytes.hasRemaining()) channel.write(bytes)
            if (record.level == JournalLevel.ERROR) channel.force(true)
        }
        enforceDiskBudget()
    }

    /**
     * Returns records that match the filters, newest first, at most [limit] of them.
     *
     * @param levels levels to include; an empty set means every level
     * @param source exact source name, or `null` for every source
     * @param sinceMillis earliest time to include, or `null` for no lower bound
     */
    fun read(
        levels: Set<JournalLevel> = emptySet(),
        source: String? = null,
        sinceMillis: Long? = null,
        limit: Int = 100,
    ): List<JournalRecord> = readSnapshot(levels, source, sinceMillis, limit).records

    /** Newest-first read; [Snapshot.truncated] tells whether the byte budget stopped it before all matches. */
    private fun readSnapshot(
        levels: Set<JournalLevel>,
        source: String?,
        sinceMillis: Long?,
        limit: Int,
    ): Snapshot {
        require(limit in 1..100_000) { "Journal limit must be between 1 and 100000" }
        val wanted = if (levels.isEmpty()) JournalLevel.values().toSet() else levels
        val sinceDate = sinceMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
        val files = wanted.flatMap(::filesOf).filter { sinceDate == null || dateOf(it)?.isBefore(sinceDate) != true }
            .sortedWith(compareByDescending<Path> { dateOf(it) }.thenByDescending(::segmentOf))
        val newest = PriorityQueue<JournalRecord>(compareBy { it.timeMillis })
        var remaining = maximumReadBytes
        var scannedDate: LocalDate? = null
        var truncated = false
        for (file in files) {
            val date = dateOf(file)
            if (newest.size >= limit && date != scannedDate) break
            scannedDate = date
            try {
                val size = Files.size(file)
                // Files are read whole and newest first: a file that does not fit is skipped, so the
                // returned records are always the newest complete set rather than an arbitrary prefix.
                if (size > remaining) { truncated = true; break }
                Files.newInputStream(file).buffered().use { input ->
                    JournalBlockCodec.visit(input, size) { record ->
                        if ((source == null || record.source == source) && (sinceMillis == null || record.timeMillis >= sinceMillis)) {
                            if (newest.size < limit) newest.add(record)
                            else if (record.timeMillis > newest.peek().timeMillis) { newest.poll(); newest.add(record) }
                        }
                    }
                }
                remaining -= size
            } catch (_: java.nio.file.NoSuchFileException) {
                // Retention can remove a segment after the reader takes its snapshot.
            }
        }
        return Snapshot(newest.sortedByDescending { it.timeMillis }, truncated)
    }

    private class Snapshot(val records: List<JournalRecord>, val truncated: Boolean)

    /**
     * Counts and retention per level for diagnostic reports. It holds no message text, so it is safe to share:
     * for each level the number of records in the last 24 hours, the time of the newest one, and the retention.
     */
    fun summary(): Map<String, Any?> {
        val since = clock.millis() - Duration.ofHours(24).toMillis()
        return JournalLevel.values().associate { level ->
            val snapshot = readSnapshot(levels = setOf(level), source = null, sinceMillis = since, limit = SUMMARY_LIMIT)
            val recent = snapshot.records
            level.fileGroup to linkedMapOf<String, Any?>(
                "recordsLast24h" to recent.size,
                "countCapped" to (recent.size >= SUMMARY_LIMIT || snapshot.truncated),
                "readBudgetExhausted" to snapshot.truncated,
                "maximumReadBytes" to maximumReadBytes,
                "maximumDiskBytes" to maximumDiskBytes,
                "newestAtMillis" to recent.firstOrNull()?.timeMillis,
                "retentionDays" to retentionDays(level),
            )
        }
    }

    /** Deletes files older than their level's retention and returns how many were removed. */
    @Synchronized
    fun prune(): Int {
        if (!Files.isDirectory(directory)) return 0
        val today = today()
        var removed = 0
        JournalLevel.values().forEach { level ->
            val keepFrom = today.minusDays(retentionDays(level))
            filesOf(level).forEach { file ->
                val date = dateOf(file) ?: return@forEach
                if (date.isBefore(keepFrom)) {
                    Files.deleteIfExists(file)
                    removed++
                }
            }
        }
        return removed + enforceDiskBudget()
    }

    /** Evicts oldest files first, but routine levels before ERROR so the disk budget never drops errors early. */
    private fun enforceDiskBudget(): Int {
        val files = JournalLevel.values().flatMap(::filesOf)
            .sortedWith(compareBy<Path> { it.fileName.toString().startsWith("${JournalLevel.ERROR.fileGroup}-") }
                .thenBy { dateOf(it) }.thenBy(::segmentOf))
        var total = files.sumOf { Files.size(it) }
        var removed = 0
        for (file in files) {
            if (total <= maximumDiskBytes) break
            val bytes = Files.size(file)
            if (Files.deleteIfExists(file)) { total -= bytes; removed++ }
        }
        return removed
    }

    private companion object {
        /** Records counted per level in [summary]; more than this is reported as "at least". */
        const val SUMMARY_LIMIT = 10_000
    }

    private fun retentionDays(level: JournalLevel): Long =
        retention.getValue(level).toDays().coerceAtLeast(1)

    private fun today(): LocalDate = LocalDate.now(clock.withZone(ZoneOffset.UTC))

    private fun segmentOf(file: Path): Int = file.fileName.toString().substringAfter('-').drop(10)
        .removePrefix("-").removeSuffix(".pnj").toIntOrNull() ?: 0

    private fun filesOf(level: JournalLevel): List<Path> {
        if (!Files.isDirectory(directory)) return emptyList()
        val prefix = "${level.fileGroup}-"
        return Files.list(directory).use { stream ->
            stream.filter { Files.isRegularFile(it) && it.fileName.toString().startsWith(prefix) && it.fileName.toString().endsWith(".pnj") }
                .sorted()
                .collect(java.util.stream.Collectors.toList())
        }
    }

    private fun dateOf(file: Path): LocalDate? =
        runCatching { LocalDate.parse(file.fileName.toString().substringAfter('-').take(10)) }
            .getOrNull()
}
