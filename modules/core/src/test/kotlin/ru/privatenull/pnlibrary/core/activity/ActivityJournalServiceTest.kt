package ru.privatenull.pnlibrary.core.activity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import ru.privatenull.pnlibrary.api.activity.ActivityEvent
import ru.privatenull.pnlibrary.api.activity.ActivityQuery
import ru.privatenull.pnlibrary.api.activity.ActivitySeverity
import java.nio.file.Files
import java.nio.charset.StandardCharsets

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

    @Test
    fun `attachments are linked, manifested, exported and restored`() {
        val root = Files.createTempDirectory("activity-attachments")
        val journal = ActivityJournalService(root)
        val event = journal.record(ActivityEvent(type = "report", severity = ActivitySeverity.ERROR))
        val attachment = journal.attach(event.eventId, "server log.txt", "text/plain", "hello".toByteArray())

        assertEquals(1, journal.exportAttachments().size)
        assertTrue(journal.exportAttachmentManifest().toString(StandardCharsets.UTF_8).contains(attachment.eventId))
        assertThrows<IllegalArgumentException> {
            journal.attach("missing", "x.txt", "text/plain", byteArrayOf(1))
        }
        journal.close()

        ActivityJournalService(root).use { restored ->
            assertEquals(1, restored.exportAttachments().size)
            assertTrue(restored.exportAttachmentManifest().toString(StandardCharsets.UTF_8).contains(attachment.id))
        }
    }

    @Test
    fun `expired events remove their attachments without touching critical ones`() {
        val now = 2_000_000_000L
        val root = Files.createTempDirectory("activity-retention")
        ActivityJournalService(root, { now }).use { journal ->
            val old = journal.record(ActivityEvent(timestamp = now - 8 * 24 * 60 * 60 * 1000L, type = "old"))
            val critical = journal.record(ActivityEvent(timestamp = now - 8 * 24 * 60 * 60 * 1000L, type = "critical", severity = ActivitySeverity.CRITICAL))
            journal.attach(old.eventId, "old.txt", "text/plain", byteArrayOf(1))
            journal.attach(critical.eventId, "critical.txt", "text/plain", byteArrayOf(2))
        }
        ActivityJournalService(root, { now }).use { restored ->
            assertEquals(1, restored.recent().size)
            assertEquals(1, restored.exportAttachments().size)
        }
    }
}
