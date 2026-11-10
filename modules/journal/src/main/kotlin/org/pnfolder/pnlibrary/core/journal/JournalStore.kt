package org.pnfolder.pnlibrary.core.journal

import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
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
import java.util.concurrent.atomic.AtomicLong

/**
 * Durable, local journal of everything the library and its plugins report.
 *
 * Records are grouped by event date into bounded segments named `<level>-<yyyy-MM-dd>[-segment].pnj`.
 * Legacy daily files remain readable. Reads keep only the selected newest records in memory and
 * scan at most [maximumReadBytes]; summary exposes an exhausted budget. Retention also caps disk use.
 * Appends write whole blocks to the end of the record day's file; the current segment is tracked in memory,
 * so the folder is listed only on the first use of a day and when the disk budget is checked.
 * ERROR records are forced to disk before [append] returns, so a crash right after an error cannot lose it.
 *
 * Retention is per level: each level's files are removed once they are older than that level's limit,
 * so an error is kept longer than a routine info line.
 *
 * @param directory journal folder, created on first use
 * @param retention maximum age of files per level
 * @param clock source of the current date for file names and pruning
 */
class JournalStore(
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

    private class Segment(val path: Path, var bytes: Long)

    private val files = JournalFiles(directory)
    private val active = HashMap<Pair<JournalLevel, LocalDate>, Segment>()
    private val loadedDays = HashSet<LocalDate>()
    private var lastPrunedOn: LocalDate? = null
    private var lastEnforcedAtMillis = clock.millis()
    private var newSegmentStarted = false

    /** Directory listings made so far; tests use it to prove that appends do not scan the folder. */
    val directoryListings: Int get() = files.listings.get()

    /** Blocks appended so far; one per chunk, not per record. */
    val blocksWritten = AtomicLong()

    /**
     * Writes [record] as one block to the file of its day and level.
     *
     * Old files are pruned once per day here, so a server that runs for weeks without a restart still
     * honours the retention limits.
     */
    fun append(record: JournalRecord) = appendAll(listOf(record)) { _, error -> throw error }

    /**
     * Writes [records] with one block per level, day and [BATCH_RECORDS] records, so a burst costs one
     * compression and one file open instead of one per record. A record that cannot be encoded is handed to
     * [rejected] and the rest is still written; I/O failures propagate. ERROR blocks are forced to disk
     * before this returns.
     */
    @Synchronized
    fun appendAll(records: List<JournalRecord>, rejected: (JournalRecord, Throwable) -> Unit) {
        if (records.isEmpty()) return
        Files.createDirectories(directory)
        pruneOncePerDay()
        records.groupByTo(LinkedHashMap()) { it.level to utcDate(it.timeMillis) }.forEach { (key, group) ->
            group.chunked(BATCH_RECORDS).forEach { writeChunk(key.first, key.second, it, rejected) }
        }
        if (newSegmentStarted || clock.millis() - lastEnforcedAtMillis >= ENFORCE_INTERVAL_MILLIS) {
            enforceDiskBudget(files.all())
        }
    }

    private fun pruneOncePerDay() {
        val today = today()
        if (lastPrunedOn == today) return
        lastPrunedOn = today
        forgetSegments()
        runCatchingNonFatal { prune() }
    }

    private fun writeChunk(
        level: JournalLevel,
        date: LocalDate,
        chunk: List<JournalRecord>,
        rejected: (JournalRecord, Throwable) -> Unit,
    ) {
        val block = try {
            JournalBlockCodec.encode(chunk).also { require(it.size <= maximumDiskBytes) { "Journal block exceeds disk budget" } }
        } catch (error: IllegalArgumentException) {
            if (chunk.size == 1) rejected(chunk[0], error) else chunk.forEach { writeChunk(level, date, listOf(it), rejected) }
            return
        }
        val segment = segmentFor(level, date, block.size)
        FileChannel.open(segment.path, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
            StandardOpenOption.APPEND).use { channel ->
            val bytes = java.nio.ByteBuffer.wrap(block)
            while (bytes.hasRemaining()) channel.write(bytes)
            if (level == JournalLevel.ERROR) channel.force(true)
        }
        segment.bytes += block.size
        blocksWritten.incrementAndGet()
    }

    /** The segment that takes [size] more bytes: the cached current one, or a new file when there is none or it is full; a new file triggers a disk budget check. */
    private fun segmentFor(level: JournalLevel, date: LocalDate, size: Int): Segment {
        if (loadedDays.add(date)) loadDay(date)
        val key = level to date
        val current = active[key]
        if (current != null && current.bytes + size <= maximumSegmentBytes) return current
        newSegmentStarted = true
        val number = current?.let { files.segmentOf(it.path) + 1 } ?: 0
        return Segment(files.pathOf(level, date, number), 0).also { active[key] = it }
    }

    /** Reads the folder once to learn the newest segment of every level on [date]. */
    private fun loadDay(date: LocalDate) {
        val all = files.all()
        for (level in JournalLevel.values()) {
            val newest = files.of(level, all).filter { files.dateOf(it) == date }.maxByOrNull(files::segmentOf) ?: continue
            active[level to date] = Segment(newest, Files.size(newest))
        }
    }

    /**
     * Returns records that match the filters, newest first, at most [limit] of them.
     *
     * @param levels levels to include; an empty set means every level
     * @param source exact source name, or `null` for every source
     * @param sinceMillis earliest time to include, or `null` for no lower bound
     * @param type record type to return; with `null` every type except [JournalRecord.STATS_TYPE]
     */
    fun read(
        levels: Set<JournalLevel> = emptySet(),
        source: String? = null,
        sinceMillis: Long? = null,
        limit: Int = 100,
        type: String? = null,
    ): List<JournalRecord> = readSnapshot(levels, source, sinceMillis, limit, type).records

    /** Newest-first read; [Snapshot.truncated] tells whether the byte budget stopped it before all matches. */
    private fun readSnapshot(
        levels: Set<JournalLevel>,
        source: String?,
        sinceMillis: Long?,
        limit: Int,
        type: String? = null,
    ): Snapshot {
        require(limit in 1..100_000) { "Journal limit must be between 1 and 100000" }
        val wanted = if (levels.isEmpty()) JournalLevel.values().toSet() else levels
        val segments = segmentsNewestFirst(wanted, sinceMillis)
        val newest = PriorityQueue<JournalRecord>(compareBy { it.timeMillis })
        var remaining = maximumReadBytes
        var scannedDate: LocalDate? = null
        var truncated = false
        for (file in segments) {
            val date = files.dateOf(file)
            if (newest.size >= limit && date != scannedDate) break
            scannedDate = date
            try {
                val size = Files.size(file)
                // Files are read whole and newest first: reading stops at a file that does not fit, so the
                // returned records are always the newest complete set rather than an arbitrary prefix.
                if (size > remaining) { truncated = true; break }
                Files.newInputStream(file).buffered().use { input ->
                    JournalBlockCodec.visit(input, size) { record ->
                        offer(newest, record, limit, source, sinceMillis, type)
                    }
                }
                remaining -= size
            } catch (_: java.nio.file.NoSuchFileException) {
                // Retention can remove a segment after the reader takes its snapshot.
            }
        }
        return Snapshot(newest.sortedByDescending { it.timeMillis }, truncated)
    }

    /** The segment files of [levels] that can hold records from [sinceMillis] on, newest day and segment first. */
    private fun segmentsNewestFirst(levels: Set<JournalLevel>, sinceMillis: Long?): List<Path> {
        val sinceDate = sinceMillis?.let(::utcDate)
        val all = files.all()
        return levels.flatMap { files.of(it, all) }.filter { sinceDate == null || files.dateOf(it)?.isBefore(sinceDate) != true }
            .sortedWith(compareByDescending<Path> { files.dateOf(it) }.thenByDescending(files::segmentOf))
    }

    /** Keeps [record] in [newest] when it matches the filters and is among the [limit] newest seen so far. */
    private fun offer(
        newest: PriorityQueue<JournalRecord>,
        record: JournalRecord,
        limit: Int,
        source: String?,
        sinceMillis: Long?,
        type: String?,
    ) {
        val typeFits = if (type == null) record.type != JournalRecord.STATS_TYPE else record.type == type
        if (!typeFits || (source != null && record.source != source) || (sinceMillis != null && record.timeMillis < sinceMillis)) return
        if (newest.size < limit) {
            newest.add(record)
        } else if (record.timeMillis > newest.peek().timeMillis) {
            newest.poll()
            newest.add(record)
        }
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

    /** Deletes segments past the retention of their level, then enforces the disk budget; returns the number of files removed. */
    @Synchronized
    fun prune(): Int {
        if (!Files.isDirectory(directory)) return 0
        val today = today()
        val survivors = ArrayList<Path>()
        var removed = 0
        for (file in files.all()) {
            val level = files.levelOf(file)
            val date = files.dateOf(file)
            if (level == null || date == null || !date.isBefore(today.minusDays(retentionDays(level)))) {
                survivors.add(file)
            } else {
                Files.deleteIfExists(file)
                removed++
            }
        }
        if (removed > 0) forgetSegments()
        return removed + enforceDiskBudget(survivors)
    }

    /** Evicts oldest files first, but routine levels before ERROR so the disk budget never drops errors early. */
    private fun enforceDiskBudget(all: List<Path>): Int {
        lastEnforcedAtMillis = clock.millis()
        newSegmentStarted = false
        val ordered = all.sortedWith(compareBy<Path> { it.fileName.toString().startsWith("${JournalLevel.ERROR.fileGroup}-") }
            .thenBy { files.dateOf(it) }.thenBy(files::segmentOf))
        var total = ordered.sumOf { Files.size(it) }
        var removed = 0
        for (file in ordered) {
            if (total <= maximumDiskBytes) break
            val bytes = Files.size(file)
            if (Files.deleteIfExists(file)) { total -= bytes; removed++ }
        }
        if (removed > 0) forgetSegments()
        return removed
    }

    /** Deleted files may be the cached ones, so the next append looks at the folder again. */
    private fun forgetSegments() {
        active.clear()
        loadedDays.clear()
    }

    private companion object {
        /** Records counted per level in [summary]; more than this is reported as "at least". */
        const val SUMMARY_LIMIT = 10_000

        /** Most records in one block, far below the decoder's limit of 10 000. */
        const val BATCH_RECORDS = 256

        /** The disk budget is checked at least this often even when no new segment starts. */
        const val ENFORCE_INTERVAL_MILLIS = 60_000L
    }

    private fun retentionDays(level: JournalLevel) = retention.getValue(level).toDays().coerceAtLeast(1)

    private fun today(): LocalDate = LocalDate.now(clock.withZone(ZoneOffset.UTC))

    private fun utcDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}
