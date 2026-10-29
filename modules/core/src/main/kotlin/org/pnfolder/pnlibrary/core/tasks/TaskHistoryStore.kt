package org.pnfolder.pnlibrary.core.tasks

import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.api.tasks.TaskId
import org.pnfolder.pnlibrary.api.tasks.TaskSnapshot
import org.pnfolder.pnlibrary.api.tasks.TaskStatus
import org.pnfolder.pnlibrary.common.io.TsvRecords
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/**
 * The finished tasks kept on disk, one tab-separated line each, so the history survives a restart.
 *
 * The first line holds the format version. Lines of the first format (16 fields) still load, with the newer fields at
 * their defaults. A line that cannot be read is skipped, so a damaged file never stops
 * the server from starting. Text fields escape tab, line break and backslash.
 */
internal class TaskHistoryStore(private val file: Path) {
    /** The saved snapshots, oldest first; empty when there is no file or it cannot be read. */
    fun load(): List<TaskSnapshot> = TsvRecords.read(file).mapNotNull { fields -> runCatching { parse(fields) }.getOrNull() }

    /** Replaces the file with [snapshots]; a failure is ignored because the history is only a convenience. */
    fun save(snapshots: List<TaskSnapshot>) {
        TsvRecords.write(file, HEADER, snapshots.map(::format))
    }

    private fun format(task: TaskSnapshot): List<Any?> = listOf(
        task.id.value, task.name, task.key, task.ownerName, task.executionKind.name, task.status.name,
        task.tags.joinToString(TAG_SEPARATOR), task.createdAt, task.nextRunAt, task.runCount, task.skippedCount,
        task.lastStartedAt, task.lastCompletedAt, task.lastFailure, task.description, task.usage,
        task.source, task.interval?.toMillis(), task.lastDuration?.toNanos(), task.cancellable, task.runnableNow, task.hidden,
    )

    private fun parse(f: List<String>): TaskSnapshot {
        require(f.size == FIELDS || f.size == SECOND_FIELDS || f.size == FIRST_FIELDS) { "Task history line has ${f.size} fields" }
        val newer = f.size >= SECOND_FIELDS
        fun text(index: Int): String? = f[index].ifEmpty { null }
        fun time(index: Int): Instant? = text(index)?.let(Instant::parse)
        return TaskSnapshot(
            id = TaskId(f[0]),
            name = text(1),
            key = text(2),
            ownerName = f[3],
            executionKind = TaskExecution.Kind.valueOf(f[4]),
            status = TaskStatus.valueOf(f[5]),
            tags = f[6].split(TAG_SEPARATOR).filter { it.isNotEmpty() }.toSet(),
            createdAt = Instant.parse(f[7]),
            nextRunAt = time(8),
            runCount = f[9].toLong(),
            skippedCount = f[10].toLong(),
            lastStartedAt = time(11),
            lastCompletedAt = time(12),
            lastFailure = text(13),
            description = text(14),
            usage = text(15),
            source = if (newer) text(16) else null,
            interval = if (newer) text(17)?.let { Duration.ofMillis(it.toLong()) } else null,
            lastDuration = if (newer) text(18)?.let { Duration.ofNanos(it.toLong()) } else null,
            cancellable = !newer || f[19].toBoolean(),
            runnableNow = !newer || f[20].toBoolean(),
            hidden = f.size == FIELDS && f[21].toBoolean(),
        )
    }

    private companion object {
        const val HEADER = "#pnlibrary-task-history 3"
        const val FIRST_FIELDS = 16
        const val SECOND_FIELDS = 21
        const val FIELDS = 22
        const val TAG_SEPARATOR = "\u001F"
    }
}
