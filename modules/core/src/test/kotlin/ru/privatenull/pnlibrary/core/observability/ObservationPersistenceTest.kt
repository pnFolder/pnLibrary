package ru.privatenull.pnlibrary.core.observability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.observability.Observation
import ru.privatenull.pnlibrary.api.observability.ObservationLevel
import ru.privatenull.pnlibrary.api.observability.ObservationRequest
import java.nio.file.Files

class ObservationPersistenceTest {
    @Test
    fun `journal reloads valid observations and skips malformed lines`() {
        val root = Files.createTempDirectory("observations")
        val observation = Observation.from(ObservationRequest(message = "started"), timestamp = 100)
        ObservationJournal(root).use { it.append(observation) }
        Files.writeString(root.resolve("observability/observations.jsonl"), "not-json\n", java.nio.file.StandardOpenOption.APPEND)

        ObservationJournal(root).use { journal ->
            assertEquals(listOf(observation), journal.recent())
        }
    }

    @Test
    fun `journal does not persist local attachment paths`() {
        val root = Files.createTempDirectory("observation-paths")
        val source = root.resolve("server.log").also { Files.writeString(it, "secret") }
        val observation = Observation.from(ObservationRequest(message = "failed", files = listOf(source)))

        ObservationJournal(root).use { it.append(observation) }

        ObservationJournal(root).use { journal ->
            assertTrue(journal.recent().single().files.isEmpty())
        }
    }

    @Test
    fun `attachment store infers content type and removes orphan`() {
        val root = Files.createTempDirectory("observation-attachments")
        val source = root.resolve("latest.log").also { Files.writeString(it, "hello") }
        val store = AttachmentStore(root)

        val attachment = store.save("event-1", source)
        assertEquals("text/plain", attachment.contentType)
        assertTrue(Files.exists(attachment.storedPath))

        store.removeOrphans(emptySet())
        assertFalse(Files.exists(attachment.storedPath))
    }

    @Test
    fun `attachment metadata survives store restart`() {
        val root = Files.createTempDirectory("attachment-reload")
        val source = root.resolve("configuration.json").also { Files.writeString(it, "{}") }
        val saved = AttachmentStore(root).save("event-1", source)

        val reloaded = AttachmentStore(root).forObservation("event-1").single()

        assertEquals(saved.copy(storedPath = reloaded.storedPath), reloaded)
        assertTrue(Files.exists(reloaded.storedPath))
    }

    @Test
    fun `attachment store rejects executable files`() {
        val root = Files.createTempDirectory("attachment-executable")
        val source = root.resolve("unsafe.jar").also { Files.writeString(it, "not really a jar") }

        assertThrows(IllegalArgumentException::class.java) {
            AttachmentStore(root).save("event-1", source)
        }
    }

    @Test
    fun `retention keeps critical observations and expires old information`() {
        val now = 10L * 24 * 60 * 60 * 1000
        val oldInfo = Observation.from(ObservationRequest(message = "old"), timestamp = 1)
        val oldCritical = Observation.from(
            ObservationRequest(message = "critical", level = ObservationLevel.CRITICAL),
            timestamp = 1,
        )

        val retained = ObservationRetention.active(listOf(oldInfo, oldCritical), now)

        assertEquals(listOf(oldCritical), retained)
    }
}
