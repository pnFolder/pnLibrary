package org.pnfolder.pnlibrary.core.stats

import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.events.EventInfo
import org.pnfolder.pnlibrary.api.tasks.TaskSnapshot
import org.pnfolder.pnlibrary.core.journal.JournalStore

/** The statistics of the server as one part: taking a snapshot, keeping hourly ones in the journal, and `/pn stats`. */
internal class StatsRuntime(
    tasks: () -> List<TaskSnapshot>,
    events: () -> List<EventInfo>,
    journal: JournalStore,
    onFailure: (Throwable) -> Unit = {},
) : AutoCloseable {
    private val collector = StatsCollector(tasks, events, journal)
    private val recorder = StatsRecorder(collector, journal, onFailure)

    @Volatile private var last: StatsSnapshot? = null

    /**
     * The numbers now. Taking them reads the journal files, so a snapshot younger than [FRESH_MILLIS] is reused: one
     * open menu or a repeated command must not read the disk again each time.
     */
    fun snapshot(): StatsSnapshot {
        last?.takeIf { System.currentTimeMillis() - it.timeMillis < FRESH_MILLIS }?.let { return it }
        return collector.collect().also { last = it }
    }

    /** The numbers as `pnlibrary` placeholders see them. */
    fun values(): Map<String, Long> = snapshot().let {
        mapOf(
            "errors" to it.errors24h.toLong(), "warnings" to it.warnings24h.toLong(), "tasks" to it.tasksActive.toLong(),
            "tasks.failed" to it.tasksFailed.toLong(), "events" to it.eventsCalled,
        )
    }

    fun command(): CommandDefinition = statsCommand(::snapshot, recorder)

    fun start() = recorder.start()

    override fun close() = recorder.close()

    private companion object {
        const val FRESH_MILLIS = 5_000L
    }
}
