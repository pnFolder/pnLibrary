package org.pnfolder.pnlibrary.core.tasks

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.api.tasks.TaskId
import org.pnfolder.pnlibrary.api.tasks.TaskQuery
import org.pnfolder.pnlibrary.api.tasks.TaskSnapshot
import org.pnfolder.pnlibrary.api.tasks.TaskStatus
import java.time.Instant

class TaskQueryMatcherTest {
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

        assertTrue(TaskQueryMatcher.matches(snapshot, query))
    }

    @Test
    fun `rejects snapshot when any criterion differs`() {
        assertFalse(TaskQueryMatcher.matches(snapshot, TaskQuery.builder().key("other").build()))
        assertFalse(TaskQueryMatcher.matches(snapshot, TaskQuery.builder().status(TaskStatus.FAILED).build()))
        assertFalse(TaskQueryMatcher.matches(snapshot, TaskQuery.builder().tag("database").build()))
    }
}
