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
import java.time.Duration
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
        source = "org.example.Saver",
        interval = Duration.ofMinutes(5),
        lastDuration = Duration.ofNanos(1_500_000),
        cancellable = false,
        runnableNow = true,
        hidden = true,
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

    @Test
    fun `a line of the first format still loads with default new fields`() {
        val file = directory.resolve("history.tsv")
        val fields = listOf(
            "id1", "Old", "", "Shop", "ASYNC", "COMPLETED", "", "2026-10-29T10:00:00Z", "", "2", "0", "", "", "", "", "",
        )
        Files.writeString(file, "#pnlibrary-task-history 1\n" + fields.joinToString("\t") + "\n")

        val loaded = TaskHistoryStore(file).load().single()

        assertEquals("Old", loaded.name)
        assertEquals(true, loaded.cancellable)
        assertEquals(null, loaded.source)
    }
}
