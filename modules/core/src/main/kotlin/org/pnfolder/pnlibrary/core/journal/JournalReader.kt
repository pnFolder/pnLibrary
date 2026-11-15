package org.pnfolder.pnlibrary.core.journal

import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.PriorityQueue

/** Newest-first reads over the journal's segment files, bounded by a byte budget. */
internal class JournalReader(private val files: JournalFiles, private val maximumReadBytes: Long) {
    /** Newest-first read; [Snapshot.truncated] tells whether the byte budget stopped it before all matches. */
    fun readSnapshot(
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
            } catch (_: NoSuchFileException) {
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

    class Snapshot(val records: List<JournalRecord>, val truncated: Boolean)

    private fun utcDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}
