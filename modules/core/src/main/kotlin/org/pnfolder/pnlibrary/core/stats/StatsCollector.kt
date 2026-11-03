package org.pnfolder.pnlibrary.core.stats

import org.pnfolder.pnlibrary.api.events.EventInfo
import org.pnfolder.pnlibrary.api.tasks.TaskSnapshot
import org.pnfolder.pnlibrary.api.tasks.TaskStatus
import org.pnfolder.pnlibrary.core.journal.JournalLevel
import org.pnfolder.pnlibrary.core.journal.JournalStore
import java.time.Duration

/** Takes a [StatsSnapshot] from the task list, the event counters and the journal. */
internal class StatsCollector(
    private val tasks: () -> List<TaskSnapshot>,
    private val events: () -> List<EventInfo>,
    private val journal: JournalStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun collect(): StatsSnapshot {
        val now = clock()
        val taskList = tasks()
        val eventList = events()
        val since = now - WINDOW.toMillis()
        val warnings = journal.read(setOf(JournalLevel.WARNING), sinceMillis = since, limit = READ_LIMIT)
        val errors = journal.read(setOf(JournalLevel.ERROR), sinceMillis = since, limit = READ_LIMIT)
        return StatsSnapshot(
            timeMillis = now,
            tasksActive = taskList.count { it.status == TaskStatus.SCHEDULED || it.status == TaskStatus.RUNNING },
            tasksFailed = taskList.count { it.status == TaskStatus.FAILED },
            taskRuns = taskList.sumOf { it.runCount },
            taskSkipped = taskList.sumOf { it.skippedCount },
            eventsCalled = eventList.sumOf { it.calls },
            eventsDenied = eventList.sumOf { it.denied },
            warnings24h = warnings.size,
            errors24h = errors.size,
            errorSources = errors.groupingBy { it.source }.eachCount().entries
                .sortedByDescending { it.value }.take(TOP_SOURCES).map { it.key to it.value },
        )
    }

    private companion object {
        val WINDOW: Duration = Duration.ofHours(24)

        /** Upper bound for one read; a count that reaches it means "at least this many". */
        const val READ_LIMIT = 5_000
        const val TOP_SOURCES = 5
    }
}
