package org.pnfolder.pnlibrary.core.runtime.details;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.commands.definition.CommandDefinition;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig;
import org.pnfolder.pnlibrary.common.concurrent.FailureThrottle;
import org.pnfolder.pnlibrary.common.concurrent.ManagedWorker;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.journal.JournalLevel;
import org.pnfolder.pnlibrary.core.journal.JournalRecord;
import org.pnfolder.pnlibrary.core.journal.store.JournalQueries;
import org.pnfolder.pnlibrary.core.journal.store.JournalStore;
import org.pnfolder.pnlibrary.core.journal.view.JournalCommand;
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership;

/** Owns journal storage, asynchronous writes, failure counters and draining at shutdown. */
public final class RuntimeJournal implements AutoCloseable {
    /** Records that may wait for the disk; a burst beyond this is counted in {@code failedWrites} and reported. */
    private static final int QUEUE_CAPACITY = 8_192;
    private static final int MAX_BATCH = 1_024;

    private final PlatformOwnership ownership;
    private final String eventsCommand;
    private final Consumer<Throwable> onWriteFailure;
    private final JournalStore store;
    private final JournalQueries queries;
    private final ManagedWorker worker = new ManagedWorker("pnLibrary-journal");
    private final AtomicLong failures = new AtomicLong();
    private final FailureThrottle writeFailureThrottle = new FailureThrottle();
    private final ConcurrentLinkedQueue<JournalRecord> pending = new ConcurrentLinkedQueue<JournalRecord>();
    private final AtomicInteger pendingCount = new AtomicInteger();
    private final AtomicBoolean drainScheduled = new AtomicBoolean();

    public RuntimeJournal(@NotNull Path journalFolder, @NotNull PnLibraryConfig config, @NotNull PlatformOwnership ownership,
                          long startedAtMillis, @NotNull String eventsCommand) {
        this(journalFolder, config, ownership, startedAtMillis, eventsCommand, failure -> { });
    }

    public RuntimeJournal(@NotNull Path journalFolder, @NotNull PnLibraryConfig config, @NotNull PlatformOwnership ownership,
                          long startedAtMillis, @NotNull String eventsCommand, @NotNull Consumer<Throwable> onWriteFailure) {
        this.ownership = ownership;
        this.eventsCommand = eventsCommand;
        this.onWriteFailure = onWriteFailure;
        Map<JournalLevel, Duration> retention = new EnumMap<JournalLevel, Duration>(JournalLevel.class);
        retention.put(JournalLevel.INFO, Duration.ofDays(config.getJournalInfoDays()));
        retention.put(JournalLevel.WARNING, Duration.ofDays(config.getJournalWarningDays()));
        retention.put(JournalLevel.ERROR, Duration.ofDays(config.getJournalErrorDays()));
        this.store = new JournalStore(journalFolder, retention);
        Failures.runCatchingNonFatal(store::prune);
        this.queries = new JournalQueries(store, config, startedAtMillis, eventsCommand);
    }

    public @NotNull JournalStore getStore() {
        return store;
    }

    public @NotNull JournalQueries getQueries() {
        return queries;
    }

    public void record(@NotNull Object owner, @NotNull LogLevel level, @NotNull String message, @Nullable Throwable error) {
        record(owner, level, message, error, Collections.<String, Object>emptyMap());
    }

    public void record(@NotNull Object owner, @NotNull LogLevel level, @NotNull String message, @Nullable Throwable error,
                       @NotNull Map<String, ?> context) {
        Map<String, String> details = new LinkedHashMap<String, String>();
        if (error != null) {
            details.put("exception", error.getClass().getName());
            details.put(JournalRecord.STACKTRACE, JournalRecord.stackTraceOf(error));
        }
        for (Map.Entry<String, ?> entry : context.entrySet()) {
            if (entry.getValue() != null) {
                details.put(entry.getKey(), entry.getValue().toString());
            }
        }
        String id = ownership.ownerDetails(owner).get("id");
        JournalRecord record = new JournalRecord(System.currentTimeMillis(), JournalLevel.of(level),
                id != null ? id : owner.getClass().getSimpleName(), error != null ? "ERROR" : "LOG", message, details);
        if (pendingCount.get() >= QUEUE_CAPACITY) {
            fail(1, new RejectedExecutionException("Journal queue is full: " + QUEUE_CAPACITY + " records wait for the disk"));
            return;
        }
        pending.add(record);
        pendingCount.incrementAndGet();
        scheduleDrain();
    }

    private void scheduleDrain() {
        if (!drainScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            worker.submit(() -> {
                drain();
                return null;
            }).exceptionally(failure -> {
                drainScheduled.set(false);
                fail(Math.max(pendingCount.get(), 1), failure.getCause() != null ? failure.getCause() : failure);
                return null;
            });
        } catch (IllegalStateException error) {
            drainScheduled.set(false);
            fail(Math.max(pendingCount.get(), 1), error);
            throw error;
        }
    }

    /** Writes everything that accumulated while the previous write ran as one batch, until the queue stays empty. */
    private void drain() {
        do {
            List<JournalRecord> batch = takeBatch();
            while (!batch.isEmpty()) {
                pendingCount.addAndGet(-batch.size());
                write(batch);
                batch = takeBatch();
            }
            drainScheduled.set(false);
        } while (!pending.isEmpty() && drainScheduled.compareAndSet(false, true));
    }

    private List<JournalRecord> takeBatch() {
        List<JournalRecord> batch = new ArrayList<JournalRecord>();
        while (batch.size() < MAX_BATCH) {
            JournalRecord next = pending.poll();
            if (next == null) {
                break;
            }
            batch.add(next);
        }
        return batch;
    }

    private void write(List<JournalRecord> batch) {
        try {
            store.appendAll(batch, (record, error) -> fail(1, error));
        } catch (Exception error) {
            fail(batch.size(), error);
        }
    }

    private void fail(int records, Throwable cause) {
        failures.addAndGet(records);
        if (writeFailureThrottle.shouldReport(cause.getClass().getName())) {
            onWriteFailure.accept(cause);
        }
    }

    /** The {@code /pn events} command over this journal. */
    public @NotNull CommandDefinition command() {
        return JournalCommand.create(store, eventsCommand);
    }

    public @NotNull Map<String, Object> summary() {
        Map<String, Object> summary = new LinkedHashMap<String, Object>(store.summary());
        summary.put("failedWrites", failures.get());
        return summary;
    }

    @Override
    public void close() {
        worker.close();
    }
}
