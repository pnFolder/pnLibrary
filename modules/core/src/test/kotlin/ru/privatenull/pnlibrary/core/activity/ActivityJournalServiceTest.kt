package ru.privatenull.pnlibrary.core.activity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.privatenull.pnlibrary.api.activity.ActivityEvent
import ru.privatenull.pnlibrary.api.activity.ActivityQuery
import ru.privatenull.pnlibrary.api.activity.ActivitySeverity
import java.nio.file.Files

class ActivityJournalServiceTest {
    @Test
    fun `persists events and filters recent entries`() {
        val root = Files.createTempDirectory("activity-journal")
        ActivityJournalService(root).use { journal ->
            journal.record(ActivityEvent(type = "update failed", severity = ActivitySeverity.ERROR, pluginId = "demo"))
            assertEquals(1, journal.recent(ActivityQuery(pluginId = "demo")).size)
            assertTrue(Files.exists(root.resolve("observability/activity.jsonl")))
        }
    }

    @Test
    fun `critical events survive clear`() {
        val root = Files.createTempDirectory("activity-journal")
        ActivityJournalService(root).use { journal ->
            journal.record(ActivityEvent(type = "critical", severity = ActivitySeverity.CRITICAL))
            journal.record(ActivityEvent(type = "info", severity = ActivitySeverity.INFO))
            journal.clear()
            assertEquals(1, journal.recent().size)
            assertThrows<IllegalStateException> { journal.close(); journal.record(ActivityEvent(type = "late")) }
        }
    }
}
