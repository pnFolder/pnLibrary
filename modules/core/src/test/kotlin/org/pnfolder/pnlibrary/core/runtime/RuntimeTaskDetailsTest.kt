package org.pnfolder.pnlibrary.core.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.api.tasks.TaskId
import org.pnfolder.pnlibrary.api.tasks.TaskSnapshot
import org.pnfolder.pnlibrary.api.tasks.TaskStatus
import java.time.Instant

class RuntimeTaskDetailsTest {
    @Test
    fun `task details preserve stable identity and aggregate failures`() {
        val snapshot = TaskSnapshot(
            id = TaskId("task-1"),
            name = "refresh",
            key = null,
            ownerName = "DemoPlugin",
            executionKind = TaskExecution.Kind.GLOBAL,
            status = TaskStatus.FAILED,
            tags = emptySet(),
            createdAt = Instant.EPOCH,
            nextRunAt = null,
            runCount = 2,
            skippedCount = 1,
            lastStartedAt = Instant.EPOCH,
            lastCompletedAt = null,
            lastFailure = "boom",
        )

        val entry = RuntimeTaskDetails.entries(listOf(snapshot)).single()
        val summary = RuntimeTaskDetails.summary(listOf(snapshot))

        assertEquals("task-1", entry["id"])
        assertEquals("GLOBAL", entry["execution"])
        assertEquals(1, summary["failedCount"])
        assertEquals(mapOf("DemoPlugin" to 1), summary["failuresByOwner"])
    }
}
