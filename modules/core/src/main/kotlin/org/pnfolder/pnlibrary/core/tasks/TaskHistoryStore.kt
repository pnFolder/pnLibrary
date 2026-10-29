package org.pnfolder.pnlibrary.core.tasks

import org.pnfolder.pnlibrary.api.tasks.TaskExecution
import org.pnfolder.pnlibrary.api.tasks.TaskId
import org.pnfolder.pnlibrary.api.tasks.TaskSnapshot
import org.pnfolder.pnlibrary.api.tasks.TaskStatus
import org.pnfolder.pnlibrary.common.io.AtomicFiles
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * The finished tasks kept on disk, one tab-separated line each, so the history survives a restart.
 *
 * The first line holds the format version; a line that cannot be read is skipped, so a damaged file never stops
 * the server from starting. Text fields escape tab, line break and backslash.
 */
internal class TaskHistoryStore(private val file: Path) {
    /** The saved snapshots, oldest first; empty when there is no file or it cannot be read. */
    fun load(): List<TaskSnapshot> {
        if (!Files.isRegularFile(file)) return emptyList()
        return runCatching {
            Files.readAllLines(file, Charsets.UTF_8).drop(1).mapNotNull { line -> runCatching { parse(line) }.getOrNull() }
        }.getOrDefault(emptyList())
    }

    /** Replaces the file with [snapshots]; a failure is ignored because the history is only a convenience. */
    fun save(snapshots: List<TaskSnapshot>) {
        val text = (listOf(HEADER) + snapshots.map(::format)).joinToString("\n", postfix = "\n")
        runCatching {
            Files.createDirectories(file.parent)
            AtomicFiles.write(file, text.toByteArray(Charsets.UTF_8))
        }
    }

    private fun format(task: TaskSnapshot): String = listOf(
        task.id.value, task.name, task.key, task.ownerName, task.executionKind.name, task.status.name,
        task.tags.joinToString(TAG_SEPARATOR), task.createdAt, task.nextRunAt, task.runCount, task.skippedCount,
        task.lastStartedAt, task.lastCompletedAt, task.lastFailure, task.description, task.usage,
    ).joinToString(TAB) { escape(it?.toString().orEmpty()) }

    private fun parse(line: String): TaskSnapshot {
        val f = line.split(TAB).map(::unescape)
        require(f.size == FIELDS) { "Task history line has ${f.size} fields" }
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
        )
    }

    private fun escape(value: String): String = buildString {
        for (char in value) {
            when (char) {
                BACKSLASH -> append(BACKSLASH).append(BACKSLASH)
                TAB.single() -> append(BACKSLASH).append('t')
                '\n' -> append(BACKSLASH).append('n')
                '\r' -> Unit
                else -> append(char)
            }
        }
    }

    private fun unescape(value: String): String {
        if (BACKSLASH !in value) return value
        val out = StringBuilder()
        var index = 0
        while (index < value.length) {
            val char = value[index]
            if (char == BACKSLASH && index + 1 < value.length) {
                index++
                out.append(
                    when (value[index]) {
                        't' -> '\t'
                        'n' -> '\n'
                        else -> value[index]
                    },
                )
            } else {
                out.append(char)
            }
            index++
        }
        return out.toString()
    }

    private companion object {
        const val HEADER = "#pnlibrary-task-history 1"
        const val FIELDS = 16
        const val TAB = "\t"
        const val BACKSLASH = '\\'
        const val TAG_SEPARATOR = "\u001F"
    }
}
