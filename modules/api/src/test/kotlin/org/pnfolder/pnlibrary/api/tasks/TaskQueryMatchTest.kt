package org.pnfolder.pnlibrary.api.tasks

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.tasks.info.TaskId
import org.pnfolder.pnlibrary.api.tasks.info.TaskQuery
import org.pnfolder.pnlibrary.api.tasks.info.TaskSnapshot
import org.pnfolder.pnlibrary.api.tasks.info.TaskStatus
import org.pnfolder.pnlibrary.api.tasks.spec.TaskExecution
import java.time.Instant

class TaskQueryMatchTest {
    private val snapshot = TaskSnapshot(
        id = TaskId("task-1"),
        name = "Refresh player cache",
        key = "cache-refresh",
        ownerName = "ExamplePlugin",
        executionKind = TaskExecution.Kind.ASYNC,
        status = TaskStatus.RUNNING,
        tags = setOf("cache", "players"),
        createdAt = Instant.EPOCH,
        nextRunAt = null,
        runCount = 2,
        skippedCount = 0,
        lastStartedAt = Instant.EPOCH,
        lastCompletedAt = null,
        lastFailure = null,
    )

    @Test
    fun `matches every populated query criterion`() {
        val query = TaskQuery.builder()
            .id(snapshot.id)
            .key("cache-refresh")
            .nameContains("PLAYER")
            .status(TaskStatus.RUNNING)
            .execution(TaskExecution.Kind.ASYNC)
            .tag("cache")
            .build()

        assertTrue(query.matches(snapshot))
    }

    @Test
    fun `rejects snapshot when any criterion differs`() {
        assertFalse(TaskQuery.builder().key("other").build().matches(snapshot))
        assertFalse(TaskQuery.builder().status(TaskStatus.FAILED).build().matches(snapshot))
        assertFalse(TaskQuery.builder().tag("database").build().matches(snapshot))
    }

    @Test
    fun `terminal statuses and the active flag agree`() {
        assertEquals(
            setOf(TaskStatus.COMPLETED, TaskStatus.CANCELLED, TaskStatus.FAILED),
            TaskStatus.entries.filter { it.isTerminal }.toSet(),
        )
        assertTrue(snapshot.isActive)
        assertFalse(snapshot.copy(status = TaskStatus.FAILED).isActive)
    }
}
