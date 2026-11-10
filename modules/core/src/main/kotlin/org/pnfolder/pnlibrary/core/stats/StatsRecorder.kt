package org.pnfolder.pnlibrary.core.stats

import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.core.journal.JournalLevel
import org.pnfolder.pnlibrary.core.journal.JournalRecord
import org.pnfolder.pnlibrary.core.journal.JournalStore
import org.pnfolder.pnlibrary.core.stats.StatsSnapshot
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Writes a [StatsSnapshot] into the journal every hour and once more at shutdown, so the black box keeps how the
 * server was doing, and reads the stored ones back for `/pn stats`.
 */
internal class StatsRecorder(
    private val collector: StatsCollector,
    private val journal: JournalStore,
    private val onFailure: (Throwable) -> Unit = {},
) : AutoCloseable {
    private val executor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "pnLibrary-stats").apply { isDaemon = true }
    }

    fun start() {
        executor.scheduleWithFixedDelay({ record() }, EVERY.toMinutes(), EVERY.toMinutes(), TimeUnit.MINUTES)
    }

    /** Stores the current numbers; a failure goes to [onFailure] and never to the caller, so statistics cannot disturb the server. */
    fun record() {
        runCatchingNonFatal {
            val snapshot = collector.collect()
            journal.append(
                JournalRecord(
                    timeMillis = snapshot.timeMillis,
                    level = JournalLevel.INFO,
                    source = PnLibraryIdentity.NAME.lowercase(),
                    type = StatsSnapshot.RECORD_TYPE,
                    message = "Статистика: задач ${snapshot.tasksActive}, ошибок за сутки ${snapshot.errors24h}",
                    details = snapshot.toDetails(),
                ),
            )
        }.onFailure(onFailure)
    }

    /** The stored snapshots of the last [days] days, newest first. */
    fun history(days: Long, limit: Int): List<StatsSnapshot> {
        val since = System.currentTimeMillis() - Duration.ofDays(days).toMillis()
        return journal.read(setOf(JournalLevel.INFO), sinceMillis = since, limit = limit, type = StatsSnapshot.RECORD_TYPE)
            .mapNotNull { StatsSnapshot.fromDetails(it.timeMillis, it.details) }
    }

    override fun close() {
        executor.shutdownNow()
        record()
    }

    private companion object {
        val EVERY: Duration = Duration.ofHours(1)
    }
}
