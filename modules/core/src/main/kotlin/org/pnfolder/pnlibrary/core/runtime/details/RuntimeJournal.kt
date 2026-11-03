package org.pnfolder.pnlibrary.core.runtime.details

import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig
import org.pnfolder.pnlibrary.common.concurrent.ManagedWorker
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.core.journal.JournalLevel
import org.pnfolder.pnlibrary.core.journal.JournalQueries
import org.pnfolder.pnlibrary.core.journal.JournalRecord
import org.pnfolder.pnlibrary.core.journal.JournalStore
import org.pnfolder.pnlibrary.core.journal.journalCommand
import org.pnfolder.pnlibrary.core.journal.toJournalLevel
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

/** Owns journal storage, asynchronous writes, failure counters and draining at shutdown. */
internal class RuntimeJournal(
    dataFolder: Path,
    config: PnLibraryConfig,
    private val ownership: PlatformOwnership,
    startedAtMillis: Long,
    private val eventsCommand: String,
) : AutoCloseable {
    val store = JournalStore(
        dataFolder.resolve("journal"),
        mapOf(
            JournalLevel.INFO to Duration.ofDays(config.journalInfoDays.toLong()),
            JournalLevel.WARNING to Duration.ofDays(config.journalWarningDays.toLong()),
            JournalLevel.ERROR to Duration.ofDays(config.journalErrorDays.toLong()),
        ),
    ).also { runCatchingNonFatal { it.prune() } }
    val queries = JournalQueries(store, config, startedAtMillis, eventsCommand)
    private val worker = ManagedWorker("pnLibrary-journal")
    private val failures = AtomicLong()

    fun record(owner: Any, level: LogLevel, message: String, error: Throwable?) {
        val record = JournalRecord(
            timeMillis = System.currentTimeMillis(),
            level = level.toJournalLevel(),
            source = ownership.ownerDetails(owner)["id"] ?: owner.javaClass.simpleName,
            type = if (error != null) "ERROR" else "LOG",
            message = message,
            details = error?.let {
                mapOf("exception" to it.javaClass.name, JournalRecord.STACKTRACE to JournalRecord.stackTraceOf(it))
            }.orEmpty(),
        )
        worker.submit { store.append(record) }.exceptionally { failures.incrementAndGet(); null }
    }

    /** The `/pn events` command over this journal. */
    fun command(): CommandDefinition = journalCommand(store, eventsCommand)

    fun summary(): Map<String, Any?> = store.summary() + ("failedWrites" to failures.get())
    override fun close() = worker.close()
}
