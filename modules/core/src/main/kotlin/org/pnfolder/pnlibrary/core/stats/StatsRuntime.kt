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
) : AutoCloseable {
    private val collector = StatsCollector(tasks, events, journal)
    private val recorder = StatsRecorder(collector, journal)

    fun snapshot(): StatsSnapshot = collector.collect()

    fun command(): CommandDefinition = statsCommand(collector, recorder)

    fun start() = recorder.start()

    override fun close() = recorder.close()
}
