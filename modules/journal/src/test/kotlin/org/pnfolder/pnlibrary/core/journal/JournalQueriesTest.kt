package org.pnfolder.pnlibrary.core.journal

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig
import java.nio.file.Path
import java.time.Clock
import java.time.Duration

class JournalQueriesTest {
    @TempDir lateinit var directory: Path

    private val now = System.currentTimeMillis()
    private val retention = JournalLevel.values().associateWith { Duration.ofDays(30) }

    private fun queries(vararg records: JournalRecord): JournalQueries {
        val store = JournalStore(directory, retention, Clock.systemUTC())
        records.forEach(store::append)
        return JournalQueries(store, PnLibraryConfig(), now, "/pn events")
    }

    private fun record(level: JournalLevel, message: String, agoMinutes: Long, details: Map<String, String> = emptyMap()) =
        JournalRecord(now - agoMinutes * MINUTE, level, "shop", "LOG", message, details)

    @Test
    fun `equal events merge into one group with a count`() {
        val groups = queries(
            record(JournalLevel.ERROR, "payout failed", 10),
            record(JournalLevel.ERROR, "payout failed", 5),
            record(JournalLevel.INFO, "opened", 1),
        ).groups(null, 24, 10)

        assertEquals(listOf("opened", "payout failed"), groups.map { it.message })
        assertEquals(2, groups.last().count)
        assertEquals(now - 5 * MINUTE, groups.last().lastMillis)
    }

    @Test
    fun `level and window and limit narrow the groups`() {
        val queries = queries(
            record(JournalLevel.ERROR, "old error", 60 * 30),
            record(JournalLevel.ERROR, "new error", 5),
            record(JournalLevel.INFO, "note", 4),
        )

        assertEquals(listOf("new error"), queries.groups("error", 24, 10).map { it.message })
        assertEquals(listOf("note"), queries.groups(null, 24, 1).map { it.message })
        assertEquals(listOf("note", "new error", "old error"), queries.groups(null, 48, 10).map { it.message })
    }

    @Test
    fun `a group shows only the first line of a long message`() {
        val groups = queries(record(JournalLevel.WARNING, "first line\nsecond line", 1)).groups(null, 24, 10)

        assertEquals("first line", groups.single().message)
    }

    @Test
    fun `detail splits the stack trace from the other facts and lists every time`() {
        val details = mapOf("player" to "Steve", JournalRecord.STACKTRACE to "boom\n at x")
        val queries = queries(
            record(JournalLevel.ERROR, "payout failed", 10, details),
            record(JournalLevel.ERROR, "payout failed", 5, details),
        )
        val id = queries.groups(null, 24, 10).single().id

        val detail = queries.detail(id)!!
        assertEquals("boom\n at x", detail.stackTrace)
        assertEquals(mapOf("player" to "Steve"), detail.details)
        assertEquals(listOf(now - 5 * MINUTE, now - 10 * MINUTE), detail.times)
    }

    @Test
    fun `detail of an unknown event is null`() {
        assertNull(queries(record(JournalLevel.INFO, "opened", 1)).detail("ffffffff"))
    }

    private companion object {
        const val MINUTE = 60_000L
    }
}
