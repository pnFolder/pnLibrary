package org.pnfolder.pnlibrary.core.events

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDate

class EventHistoryTest {
    @Test
    fun `counts calls per day and survives a restart`(@TempDir dir: Path) {
        val file = dir.resolve("history.tsv")
        EventHistory(file, 30).use { history ->
            history.record("a.Buy", denied = false)
            history.record("a.Buy", denied = true)
            history.record("a.Join", denied = false)
        }
        val reloaded = EventHistory(file, 30)
        val buy = reloaded.entries("a.Buy").single()
        assertEquals(2L, buy.calls)
        assertEquals(1L, buy.denied)
        assertEquals(LocalDate.now(), buy.date)
        assertEquals(2, reloaded.entries().size)
    }

    @Test
    fun `drops days older than the retention and keeps nothing at zero`(@TempDir dir: Path) {
        val file = dir.resolve("history.tsv")
        val old = LocalDate.now().minusDays(40)
        val recent = LocalDate.now().minusDays(2)
        Files.write(file, ("$old\ta.Buy\t5\t0\n$recent\ta.Buy\t3\t1\nbroken line\n").toByteArray(Charsets.UTF_8))
        assertEquals(listOf(recent), EventHistory(file, 30).entries().map { it.date })
        val off = EventHistory(file, 0)
        off.record("a.Buy", denied = false)
        assertTrue(off.entries().isEmpty())
    }

    @Test
    fun `counts calls per hour and survives a restart`(@TempDir dir: Path) {
        val file = dir.resolve("history.tsv")
        EventHistory(file, 30).use { history ->
            history.record("a.Buy", denied = false)
            history.record("a.Buy", denied = true)
        }
        val hour = EventHistory(file, 30).hourly("a.Buy").single()
        assertEquals(2L, hour.calls)
        assertEquals(1L, hour.denied)
        assertEquals(java.time.LocalDateTime.now().truncatedTo(java.time.temporal.ChronoUnit.HOURS), hour.hour)
    }

    @Test
    fun `drops hours older than 30 days`(@TempDir dir: Path) {
        val file = dir.resolve("history.tsv")
        val old = java.time.LocalDateTime.now().minusDays(40).truncatedTo(java.time.temporal.ChronoUnit.HOURS)
        Files.write(file, "$old\ta.Buy\t5\t0\n".toByteArray(Charsets.UTF_8))
        assertTrue(EventHistory(file, 60).hourly().isEmpty())
    }

    @Test
    fun `exports days and hours as csv and json beside the history file`(@TempDir dir: Path) {
        val file = dir.resolve("history.tsv")
        EventHistory(file, 30).use { history ->
            history.record("a.Buy", denied = true)
            val csv = Files.readString(history.export(json = false)!!)
            assertTrue(csv.startsWith("period,time,event,calls,denied"), csv)
            assertTrue(csv.contains("day,") && csv.contains("hour,") && csv.contains(",a.Buy,1,1"), csv)
            val json = Files.readString(history.export(json = true)!!)
            assertTrue(json.contains("\"event\":\"a.Buy\"") && json.contains("\"hours\":["), json)
        }
        assertTrue(EventHistory(null, 30).export(json = false) == null)
    }
}
