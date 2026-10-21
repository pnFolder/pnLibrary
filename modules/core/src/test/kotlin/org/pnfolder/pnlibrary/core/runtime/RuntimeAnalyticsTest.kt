package org.pnfolder.pnlibrary.core.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.core.runtime.details.RuntimeAnalytics

class RuntimeAnalyticsTest {
    @Test
    fun `critical runtime exposes task and update signals`() {
        val analytics = RuntimeAnalytics.of(
            taskCount = 3,
            updateCount = 2,
            taskFailures = 1,
            failedUpdates = 1,
            diagnosticSummary = mapOf("count" to 4),
            history = mapOf("occurrenceCount" to 2, "incidentCount" to 1),
            nativeLogs = mapOf("count" to 5),
            reports = mapOf("created" to 6, "failed" to 1, "successRate" to 0.5),
            observability = mapOf("errorCount" to 2),
        )

        assertEquals("critical", analytics["status"])
        assertEquals(3, (analytics["signals"] as List<*>).size)
        assertEquals(3, (analytics["resources"] as Map<*, *>)["registeredTasks"])
        assertEquals(2, (analytics["counts"] as Map<*, *>)["observabilityErrors"])
    }

    @Test
    fun `healthy runtime has no signals`() {
        val analytics = RuntimeAnalytics.of(
            taskCount = 0,
            updateCount = 0,
            taskFailures = 0,
            failedUpdates = 0,
            diagnosticSummary = emptyMap(),
            history = emptyMap(),
            nativeLogs = emptyMap(),
            reports = emptyMap(),
            observability = emptyMap(),
        )

        assertEquals("healthy", analytics["status"])
        assertTrue((analytics["signals"] as List<*>).isEmpty())
    }
}
