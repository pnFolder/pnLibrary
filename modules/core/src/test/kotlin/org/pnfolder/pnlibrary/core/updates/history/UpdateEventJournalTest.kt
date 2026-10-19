package org.pnfolder.pnlibrary.core.updates.history

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.updates.*
import org.pnfolder.pnlibrary.api.version.SemanticVersion
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class UpdateEventJournalTest {
    @TempDir lateinit var directory: Path
    private val instant = Instant.parse("2026-10-17T12:00:00Z")
    private val clock = Clock.fixed(instant, ZoneOffset.UTC)

    @Test
    fun `initiator and operation survive restart without being inferred from state`() {
        val actor = UpdateActor(UpdateActor.Kind.PLAYER, UUID.randomUUID().toString(), "Admin")
        UpdateEventJournal(directory, clock).record(snapshot(1, "pnlibrary"), actor, "STAGE")
        val restored = UpdateEventJournal(directory).read().single()
        assertEquals(actor, restored.actor)
        assertEquals("STAGE", restored.operation)
        assertNotEquals(UpdateActor.SYSTEM, restored.actor)
    }

    @Test
    fun `observations survive reopening with exact time and version transitions`() {
        val journal = UpdateEventJournal(directory, clock)
        val snapshot = snapshot(1, "pnlibrary")
        val event = journal.record(snapshot)
        val restored = UpdateEventJournal(directory).read().single()
        assertEquals(event, restored)
        assertEquals(instant, restored.occurredAt)
        assertEquals(snapshot.id, restored.planId)
        assertEquals(listOf(UpdateEventJournal.Change("pnlibrary", "1.0.0", "1.1.0")), restored.changes)
        assertEquals(1, Files.list(directory).use { it.count() })
    }

    @Test
    fun `pages are newest first and product filters apply before pagination`() {
        val journal = UpdateEventJournal(directory, clock)
        journal.record(snapshot(1, "pnlibrary"))
        journal.record(snapshot(2, "currency"))
        journal.record(snapshot(3, "pnlibrary"))
        assertEquals(listOf(3L, 2L), journal.read(pageSize = 2).map { it.revision })
        assertEquals(listOf(1L), journal.read(page = 2, pageSize = 2).map { it.revision })
        assertEquals(listOf(3L, 1L), journal.read(product = "PNLIBRARY").map { it.revision })
        assertTrue(journal.read(page = Int.MAX_VALUE).isEmpty())
        assertThrows(IllegalArgumentException::class.java) { journal.read(page = 0) }
        assertThrows(IllegalArgumentException::class.java) { journal.read(pageSize = 101) }
    }

    @Test
    fun `corrupt entries do not hide valid history and temporary files are ignored`() {
        val warnings = mutableListOf<String>()
        val journal = UpdateEventJournal(directory, clock, warnings::add)
        journal.record(snapshot(1, "pnlibrary"))
        Files.writeString(directory.resolve("broken.json"), "not json")
        Files.writeString(directory.resolve("unfinished.tmp"), "partial")
        assertEquals(1, journal.read().size)
        assertEquals(1, warnings.size)
        assertTrue(Files.exists(directory.resolve("broken.json")))
    }

    @Test
    fun `an observation identical to the previous one is not stored again`() {
        val journal = UpdateEventJournal(directory, clock)
        journal.record(snapshot(1, "pnlibrary"))
        journal.record(snapshot(2, "pnlibrary"))
        assertEquals(1, journal.count())

        journal.record(snapshot(3, "currency"))
        journal.record(snapshot(4, "pnlibrary"))
        assertEquals(3, journal.count())
        assertEquals(2, journal.count(product = "pnlibrary"))
    }

    @Test
    fun `duplicate suppression also applies right after reopening the journal`() {
        UpdateEventJournal(directory, clock).record(snapshot(1, "pnlibrary"))
        UpdateEventJournal(directory, clock).record(snapshot(2, "pnlibrary"))
        assertEquals(1, UpdateEventJournal(directory, clock).count())
    }

    @Test
    fun `only the newest events within the maximum age are kept`() {
        val journal = UpdateEventJournal(directory, clock, maxAge = Duration.ofDays(30), maxEvents = 2)
        journal.record(snapshot(1, "a"))
        journal.record(snapshot(2, "b"))
        journal.record(snapshot(3, "c"))
        assertEquals(2, journal.prune().let { journal.count() })

        val files = Files.list(directory).use { it.toList() }
        Files.setLastModifiedTime(files.first(), FileTime.from(instant.minusSeconds(40L * 86_400)))
        assertEquals(1, journal.prune())
        assertEquals(1, journal.count())
    }

    @Test
    fun `checks that found nothing are kept on disk but left out of the history people read`() {
        val journal = UpdateEventJournal(directory, clock)
        val quiet = UpdatePlanSnapshot(UUID.randomUUID(), 1, UpdateState.UP_TO_DATE, null, emptyList(), null)
        journal.record(quiet, UpdateActor.SYSTEM, "CHECK")
        journal.record(snapshot(2, "pnlibrary"), UpdateActor.SYSTEM, "CHECK")
        assertEquals(listOf(2L), journal.read().map { it.revision })
        assertEquals(1, journal.count())
        assertEquals(2, Files.list(directory).use { it.count() })
    }

    private fun snapshot(revision: Long, product: String) = UpdatePlanSnapshot(
        UUID.randomUUID(), revision, UpdateState.UPDATE_AVAILABLE,
        UpdatePlan(8, listOf(ProductChange(ProductId.of(product), SemanticVersion.parse("1.0.0"), SemanticVersion.parse("1.1.0"))), emptyList()),
        emptyList(), null,
    )
}
