package org.pnfolder.pnlibrary.core.lifecycle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StatsSnapshotTest {
    private val snapshot = StatsSnapshot(
        timeMillis = 1_000,
        tasksActive = 4,
        tasksFailed = 1,
        taskRuns = 120,
        taskSkipped = 3,
        eventsCalled = 9_000,
        eventsDenied = 7,
        warnings24h = 12,
        errors24h = 5,
        errorSources = listOf("pnmarket" to 3, "pnclans" to 2),
    )

    @Test
    fun `a stored snapshot reads back the same`() {
        assertEquals(snapshot, StatsSnapshot.fromDetails(1_000, snapshot.toDetails()))
    }

    @Test
    fun `a record with a missing number is ignored`() {
        assertNull(StatsSnapshot.fromDetails(1_000, snapshot.toDetails() - "taskRuns"))
    }

    @Test
    fun `stored details hold only numbers and plugin ids`() {
        val details = snapshot.toDetails()
        assertEquals(setOf("errorSources"), details.filterValues { !it.all(Char::isDigit) }.keys)
        assertTrue(details.getValue("errorSources").matches(Regex("[a-z0-9_]+:\\d+(,[a-z0-9_]+:\\d+)*")))
    }
}
