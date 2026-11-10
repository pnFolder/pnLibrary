package org.pnfolder.pnlibrary.core.observability

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.observability.Observation
import org.pnfolder.pnlibrary.api.observability.ObservationKeys
import org.pnfolder.pnlibrary.api.observability.ObservationLevel
import org.pnfolder.pnlibrary.api.observability.ObservationRequest
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger

class ObservationJournalThrottleTest {
    private val day = 24L * 60 * 60 * 1000

    private fun observation(index: Int, at: Long, level: ObservationLevel = ObservationLevel.INFO, keepFor: Long? = null): Observation {
        val data = if (keepFor == null) emptyMap() else mapOf(ObservationKeys.KEEP_UNTIL_MS to (at + keepFor).toString())
        return Observation.from(ObservationRequest(message = "event $index", level = level, data = data), timestamp = at)
    }

    @Test
    fun `recent equals what retention on every append would have kept, also after a reload`() {
        val root = Files.createTempDirectory("observation-equivalence")
        var now = 0L
        val appended = ArrayList<Observation>()
        ObservationJournal(root, clock = { now }).use { journal ->
            repeat(400) { index ->
                now = index * (day / 20)
                val level = ObservationLevel.values()[index % ObservationLevel.values().size]
                val keep = if (index % 11 == 0) 3 * day else null
                val observation = observation(index, now, level, keep)
                appended += observation
                journal.append(observation)
                assertEquals(ObservationRetention.active(appended, now), journal.recent(), "after append $index")
            }
        }
        ObservationJournal(root, clock = { now }).use { reloaded ->
            assertEquals(ObservationRetention.active(appended, now), reloaded.recent())
        }
    }

    @Test
    fun `ten thousand appends run retention at most ten times`() {
        val root = Files.createTempDirectory("observation-throttle")
        val calls = AtomicInteger()
        val journal = ObservationJournal(root, clock = { 1_000 }, retain = { all, now ->
            calls.incrementAndGet()
            ObservationRetention.active(all, now)
        })
        journal.use { repeat(10_000) { index -> it.append(observation(index, 1_000)) } }

        assertTrue(calls.get() <= 10, "retention calls: ${calls.get()}")
        ObservationJournal(root, clock = { 1_000 }).use { assertEquals(10_000, it.recent().size) }
    }

    @Test
    fun `a full journal is cut by margin rather than rewritten on every append`() {
        val root = Files.createTempDirectory("observation-bound")
        val calls = AtomicInteger()
        ObservationJournal(root, clock = { 1_000 }, retain = { all, now ->
            calls.incrementAndGet()
            ObservationRetention.active(all, now)
        }).use { journal ->
            repeat(ObservationRetention.MAX_OBSERVATIONS + 1_200) { journal.append(observation(it, 1_000)) }
            assertEquals(ObservationRetention.MAX_OBSERVATIONS, journal.recent().size)
            assertEquals("event 1200", journal.recent().first().message)
        }
        assertTrue(calls.get() <= 5, "retention calls: ${calls.get()}")
    }
}
