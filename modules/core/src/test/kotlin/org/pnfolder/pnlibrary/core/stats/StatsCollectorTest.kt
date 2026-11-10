package org.pnfolder.pnlibrary.core.stats

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.events.EventInfo
import org.pnfolder.pnlibrary.core.journal.JournalLevel
import org.pnfolder.pnlibrary.core.journal.JournalRecord
import org.pnfolder.pnlibrary.core.journal.JournalStore
import java.nio.file.Path
import java.time.Duration

class StatsCollectorTest {
    @TempDir lateinit var directory: Path

    private val retention = JournalLevel.values().associateWith { Duration.ofDays(30) }
    private val now = System.currentTimeMillis()

    private fun journal() = JournalStore(directory, retention)

    private fun record(level: JournalLevel, source: String, ageHours: Long = 0) =
        JournalRecord(now - Duration.ofHours(ageHours).toMillis(), level, source, "LOG", "$source $level")

    private fun collector(journal: JournalStore, events: List<EventInfo> = emptyList()) =
        StatsCollector(tasks = { emptyList() }, events = { events }, journal = journal, clock = { now })

    @Test
    fun `counts the errors and warnings of the last day and ranks the plugins`() {
        val journal = journal()
        repeat(3) { journal.append(record(JournalLevel.ERROR, "pnmarket")) }
        journal.append(record(JournalLevel.ERROR, "pnclans"))
        journal.append(record(JournalLevel.ERROR, "pnclans", ageHours = 48))
        journal.append(record(JournalLevel.WARNING, "pnmarket"))

        val snapshot = collector(journal).collect()

        assertEquals(4, snapshot.errors24h)
        assertEquals(1, snapshot.warnings24h)
        assertEquals(listOf("pnmarket" to 3, "pnclans" to 1), snapshot.errorSources)
    }

    @Test
    fun `adds up the calls and denials of every event`() {
        val events = listOf(EventInfo("a.One", 2, 10, 1), EventInfo("a.Two", 1, 5, 0))

        val snapshot = collector(journal(), events).collect()

        assertEquals(15, snapshot.eventsCalled)
        assertEquals(1, snapshot.eventsDenied)
    }

    @Test
    fun `a stored snapshot is read back and does not show up as a journal event`() {
        val journal = journal()
        journal.append(record(JournalLevel.ERROR, "pnmarket"))
        val recorder = StatsRecorder(collector(journal), journal)

        recorder.record()

        val history = recorder.history(days = 1, limit = 5)
        assertEquals(1, history.size)
        assertEquals(1, history.single().errors24h)
        assertTrue(journal.read(levels = setOf(JournalLevel.INFO)).none { it.type == StatsSnapshot.RECORD_TYPE })
    }

    @Test
    fun `a snapshot that cannot be taken is reported and does not reach the caller`() {
        val journal = journal()
        val failures = mutableListOf<Throwable>()
        val broken = StatsCollector({ throw IllegalStateException("tasks unavailable") }, { emptyList() }, journal)

        StatsRecorder(broken, journal) { failures += it }.record()

        assertEquals(listOf("tasks unavailable"), failures.map { it.message })
    }
}
