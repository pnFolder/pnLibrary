package org.pnfolder.pnlibrary.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.api.tasks.TaskId
import org.pnfolder.pnlibrary.api.tasks.TaskSnapshot
import org.pnfolder.pnlibrary.api.tasks.TaskStatus
import org.pnfolder.pnlibrary.core.tasks.TaskHistoryStore
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

class TaskHistoryStoreTest {
    @TempDir
    lateinit var directory: Path

    private fun task(id: String, failure: String? = null) = TaskSnapshot(
        id = TaskId(id),
        name = "Save\tplayers",
        key = null,
        ownerName = "Shop",
        executionKind = TaskExecution.Kind.ASYNC,
        status = if (failure == null) TaskStatus.COMPLETED else TaskStatus.FAILED,
        tags = setOf("a", "b"),
        createdAt = Instant.parse("2026-10-29T10:00:00Z"),
        nextRunAt = null,
        runCount = 3,
        skippedCount = 1,
        lastStartedAt = Instant.parse("2026-10-29T10:00:01Z"),
        lastCompletedAt = Instant.parse("2026-10-29T10:00:02Z"),
        lastFailure = failure,
        description = "Writes players",
        usage = null,
    )

    @Test
    fun `saved tasks come back unchanged, including tabs and line breaks`() {
        val store = TaskHistoryStore(directory.resolve("history.tsv"))
        val saved = listOf(task("one"), task("two", "IllegalStateException: boom\n\tat x\\y"))

        store.save(saved)

        assertEquals(saved, store.load())
    }

    @Test
    fun `a missing or damaged file gives the readable lines only`() {
        val file = directory.resolve("history.tsv")
        val store = TaskHistoryStore(file)
        assertEquals(emptyList<TaskSnapshot>(), store.load())

        store.save(listOf(task("one")))
        Files.writeString(file, Files.readString(file) + "garbage line\n")

        assertEquals(listOf(task("one")), store.load())
    }
}
