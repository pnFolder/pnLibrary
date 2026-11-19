package org.pnfolder.pnlibrary.core.journal.store

import org.pnfolder.pnlibrary.core.journal.JournalRecord
import org.pnfolder.pnlibrary.core.journal.JournalLevel
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
import java.util.concurrent.atomic.AtomicLong

// Segments: `<level>-<yyyy-MM-dd>[-n].pnj`; active segment tracked in memory so the folder is listed only on first use per day.
// ERROR blocks are forced to disk before append() returns — a crash right after cannot lose an error record.
// Retention is per level so errors are kept longer than routine info lines.
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
    private val reader = JournalReader(files, maximumReadBytes)
    private val active = HashMap<Pair<JournalLevel, LocalDate>, Segment>()
    private val loadedDays = HashSet<LocalDate>()
    private var lastPrunedOn: LocalDate? = null
    private var lastEnforcedAtMillis = clock.millis()
    private var newSegmentStarted = false

    // Tests use it to prove that appends do not scan the folder.
    val directoryListings: Int get() = files.listings.get()

    // One per chunk, not per record.
    val blocksWritten = AtomicLong()

    fun append(record: JournalRecord) = appendAll(listOf(record)) { _, error -> throw error }

    // One block per level/day/BATCH_RECORDS so a burst costs one compress + one file open, not one per record.
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

    // A new segment triggers an async disk-budget check to avoid accumulating too many files.
    private fun segmentFor(level: JournalLevel, date: LocalDate, size: Int): Segment {
        if (loadedDays.add(date)) loadDay(date)
        val key = level to date
        val current = active[key]
        if (current != null && current.bytes + size <= maximumSegmentBytes) return current
        newSegmentStarted = true
        val number = current?.let { files.segmentOf(it.path) + 1 } ?: 0
        return Segment(files.pathOf(level, date, number), 0).also { active[key] = it }
    }

    private fun loadDay(date: LocalDate) {
        val all = files.all()
        for (level in JournalLevel.values()) {
            val newest = files.of(level, all).filter { files.dateOf(it) == date }.maxByOrNull(files::segmentOf) ?: continue
            active[level to date] = Segment(newest, Files.size(newest))
        }
    }

    fun read(
        levels: Set<JournalLevel> = emptySet(),
        source: String? = null,
        sinceMillis: Long? = null,
        limit: Int = 100,
        type: String? = null,
    ): List<JournalRecord> = reader.readSnapshot(levels, source, sinceMillis, limit, type).records

    fun summary(): Map<String, Any?> {
        val since = clock.millis() - Duration.ofHours(24).toMillis()
        return JournalLevel.values().associate { level ->
            val snapshot = reader.readSnapshot(levels = setOf(level), source = null, sinceMillis = since, limit = SUMMARY_LIMIT)
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

    // Routine levels evicted before ERROR — the disk budget must never force-remove error records early.
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

    private fun forgetSegments() {
        active.clear()
        loadedDays.clear()
    }

    private companion object {
        // More than SUMMARY_LIMIT records in 24 h is reported as "at least".
        const val SUMMARY_LIMIT = 10_000
        // Far below the decoder's limit of 10 000.
        const val BATCH_RECORDS = 256
        // Disk budget is checked at least this often even when no new segment starts.
        const val ENFORCE_INTERVAL_MILLIS = 60_000L
    }

    private fun retentionDays(level: JournalLevel) = retention.getValue(level).toDays().coerceAtLeast(1)

    private fun today(): LocalDate = LocalDate.now(clock.withZone(ZoneOffset.UTC))

    private fun utcDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}
