package org.pnfolder.pnlibrary.core.observability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.observability.Observation
import org.pnfolder.pnlibrary.api.observability.ObservationLevel
import org.pnfolder.pnlibrary.api.observability.ObservationRequest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class ObservationPersistenceTest {
    @Test
    fun `journal reloads valid observations and skips malformed lines`() {
        val root = Files.createTempDirectory("observations")
        val observation = Observation.from(ObservationRequest(message = "started"), timestamp = 100)
        ObservationJournal(root, clock = { 100 }).use { it.append(observation) }
        Files.writeString(root.resolve("observability/observations.jsonl"), "not-json\n", StandardOpenOption.APPEND)

        ObservationJournal(root, clock = { 100 }).use { journal ->
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
            assertEquals("failed", journal.recent().single().message)
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

    @Test
    fun `runtime removes expired observations during startup`() {
        val root = Files.createTempDirectory("observation-retention")
        val old = Observation.from(ObservationRequest(message = "expired"), timestamp = 1)
        ObservationJournal(root, clock = { 1 }).use { it.append(old) }

        val runtime = ObservationRuntimeFixture.openAfterThirtyDays(root)

        assertTrue(runtime.recent().isEmpty())
    }

    @Test
    fun `journal applies retention while new observations are recorded`() {
        val root = Files.createTempDirectory("observation-live-retention")
        val old = Observation.from(ObservationRequest(message = "expired"), timestamp = 1)
        val current = Observation.from(ObservationRequest(message = "current"), timestamp = 30L * 24 * 60 * 60 * 1000)
        ObservationJournal(root, clock = { current.timestamp }).use { journal ->
            journal.append(old)
            journal.append(current)

            assertEquals(listOf(current), journal.recent())
        }
    }

    @Test
    fun `author retention overrides level in both directions`() {
        val day = 24L * 60 * 60 * 1000
        var now = 0L
        val runtime = ObservabilityRuntime(Files.createTempDirectory("observation-keep"), clock = { now })
        runtime.capture {
            message("short critical"); level(ObservationLevel.CRITICAL); keepFor(java.time.Duration.ofDays(2))
        }
        runtime.capture {
            message("long info"); level(ObservationLevel.INFO); keepFor(java.time.Duration.ofDays(365))
        }
        runtime.capture { message("plain info") }

        now = 10 * day
        runtime.maintain()

        assertEquals(listOf("long info"), runtime.recent().map { it.message })
        assertEquals(365 * day, runtime.recent().single().data["pn.keepUntilMs"]!!.toLong())
        assertTrue(runtime.recent().single().data.keys.none { it == "pn.keepForMs" })
    }

    @Test
    fun `report journal has readable dates and expiry`() {
        val root = Files.createTempDirectory("observation-report")
        val runtime = ObservabilityRuntime(root, clock = { 0 })
        runtime.capture { plugin("pnMarket"); source("auction"); message("lot lost"); keepFor(java.time.Duration.ofDays(1)) }

        val line = String(runtime.reportSnapshot().journal, Charsets.UTF_8)

        assertTrue(line.contains("\"timeUtc\":\"1970-01-01T00:00:00Z\""), line)
        assertTrue(line.contains("\"expiresUtc\":\"1970-01-02T00:00:00Z\""), line)
        assertTrue(line.contains("\"source\":\"auction\""), line)
    }

    private object ObservationRuntimeFixture {
        fun openAfterThirtyDays(root: Path): ObservabilityRuntime = ObservabilityRuntime(
            root,
            clock = { 30L * 24 * 60 * 60 * 1000 },
        )
    }
}
