package org.pnfolder.pnlibrary.core.runtime.details

import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig
import org.pnfolder.pnlibrary.common.concurrent.ManagedWorker
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.core.journal.JournalLevel
import org.pnfolder.pnlibrary.core.journal.JournalRecord
import org.pnfolder.pnlibrary.core.journal.JournalStore
import org.pnfolder.pnlibrary.core.journal.toJournalLevel
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

/** Owns journal storage, asynchronous writes, failure counters and draining at shutdown. */
internal class RuntimeJournal(dataFolder: Path, config: PnLibraryConfig, private val ownership: PlatformOwnership) : AutoCloseable {
    val store = JournalStore(
        dataFolder.resolve("journal"),
        mapOf(
            JournalLevel.INFO to Duration.ofDays(config.journalInfoDays.toLong()),
            JournalLevel.WARNING to Duration.ofDays(config.journalWarningDays.toLong()),
            JournalLevel.ERROR to Duration.ofDays(config.journalErrorDays.toLong()),
        ),
    ).also { runCatchingNonFatal { it.prune() } }
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

    fun summary(): Map<String, Any?> = store.summary() + ("failedWrites" to failures.get())
    override fun close() = worker.close()
}
