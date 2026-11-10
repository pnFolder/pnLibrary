package org.pnfolder.pnlibrary.core.runtime.details

import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig
import org.pnfolder.pnlibrary.common.concurrent.FailureThrottle
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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Owns journal storage, asynchronous writes, failure counters and draining at shutdown. */
internal class RuntimeJournal(
    dataFolder: Path,
    config: PnLibraryConfig,
    private val ownership: PlatformOwnership,
    startedAtMillis: Long,
    private val eventsCommand: String,
    private val onWriteFailure: (Throwable) -> Unit = {},
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
    private val writeFailureThrottle = FailureThrottle()
    private val pending = ConcurrentLinkedQueue<JournalRecord>()
    private val pendingCount = AtomicInteger()
    private val drainScheduled = AtomicBoolean()

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
        if (pendingCount.get() >= QUEUE_CAPACITY) {
            fail(1, RejectedExecutionException("Journal queue is full: $QUEUE_CAPACITY records wait for the disk"))
            return
        }
        pending.add(record)
        pendingCount.incrementAndGet()
        scheduleDrain()
    }

    private fun scheduleDrain() {
        if (!drainScheduled.compareAndSet(false, true)) return
        try {
            worker.submit(::drain).exceptionally { failure ->
                drainScheduled.set(false)
                fail(pendingCount.get().coerceAtLeast(1), failure.cause ?: failure)
                null
            }
        } catch (error: IllegalStateException) {
            drainScheduled.set(false)
            fail(pendingCount.get().coerceAtLeast(1), error)
            throw error
        }
    }

    /** Writes everything that accumulated while the previous write ran as one batch, until the queue stays empty. */
    private fun drain() {
        do {
            while (true) {
                val batch = ArrayList<JournalRecord>()
                while (batch.size < MAX_BATCH) batch.add(pending.poll() ?: break)
                if (batch.isEmpty()) break
                pendingCount.addAndGet(-batch.size)
                write(batch)
            }
            drainScheduled.set(false)
        } while (pending.isNotEmpty() && drainScheduled.compareAndSet(false, true))
    }

    private fun write(batch: List<JournalRecord>) {
        try {
            store.appendAll(batch) { _, error -> fail(1, error) }
        } catch (error: Exception) {
            fail(batch.size, error)
        }
    }

    private fun fail(records: Int, cause: Throwable) {
        failures.addAndGet(records.toLong())
        if (writeFailureThrottle.shouldReport(cause.javaClass.name)) onWriteFailure(cause)
    }

    /** The `/pn events` command over this journal. */
    fun command(): CommandDefinition = journalCommand(store, eventsCommand)

    fun summary(): Map<String, Any?> = store.summary() + ("failedWrites" to failures.get())
    override fun close() = worker.close()

    private companion object {
        /** Records that may wait for the disk; a burst beyond this is counted in `failedWrites` and reported. */
        const val QUEUE_CAPACITY = 8_192
        const val MAX_BATCH = 1_024
    }
}
