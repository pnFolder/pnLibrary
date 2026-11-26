package org.pnfolder.pnlibrary.currency;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount;
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor;
import org.pnfolder.pnlibrary.api.currency.CurrencyHistoryPage;
import org.pnfolder.pnlibrary.api.currency.CurrencyHistoryQuery;
import org.pnfolder.pnlibrary.api.currency.CurrencyImportMode;
import org.pnfolder.pnlibrary.api.currency.CurrencyImportResult;
import org.pnfolder.pnlibrary.api.currency.CurrencyKey;
import org.pnfolder.pnlibrary.api.currency.CurrencyStorage;
import org.pnfolder.pnlibrary.api.currency.CurrencyStorageSnapshot;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionRequest;
import org.pnfolder.pnlibrary.common.concurrent.ManagedWorker;

/**
 * Single-writer JSON storage with group commit: transactions that wait together are applied one after another and saved
 * once. Each result is reported only after the file holding it has reached the disk.
 */
final class FileCurrencyStorage implements CurrencyStorage {
    private static final int MAXIMUM_BATCH = 500;

    private final Path file;
    private final int maximumTransactions;
    private final FileReplacer replaceFile;
    private final ManagedWorker worker = new ManagedWorker("pnLibrary-currency-file");
    private final CurrencyLedgerState state = new CurrencyLedgerState();
    private final ConcurrentLinkedQueue<StorageJob> jobs = new ConcurrentLinkedQueue<StorageJob>();
    private final AtomicBoolean drainScheduled = new AtomicBoolean();
    private final CurrencyFileWriter writer;
    private final FileLedgerImport ledgerImport;

    FileCurrencyStorage(@NotNull Path file, int maximumTransactions) {
        this(file, maximumTransactions, FileReplacer.durable());
    }

    FileCurrencyStorage(@NotNull Path file, int maximumTransactions, @NotNull FileReplacer replaceFile) {
        this(file, maximumTransactions, () -> { }, replaceFile);
    }

    /** Opens the storage and reads the file when it exists; {@code encoded} is called for each transaction encoded. */
    FileCurrencyStorage(@NotNull Path file, int maximumTransactions, @NotNull Runnable encoded, @NotNull FileReplacer replaceFile) {
        if (maximumTransactions < 100 || maximumTransactions > 1_000_000) {
            throw new IllegalArgumentException("maximumTransactions must be between 100 and 1000000");
        }
        this.file = file;
        this.maximumTransactions = maximumTransactions;
        this.replaceFile = replaceFile;
        this.writer = new CurrencyFileWriter(encoded);
        this.ledgerImport = new FileLedgerImport(state, maximumTransactions);
        CurrencyFileLoader.load(file, state);
    }

    @Override
    @NotNull
    public CompletionStage<BigDecimal> balance(@NotNull CurrencyKey currency, @NotNull CurrencyAccount account) {
        return async(() -> {
            BigDecimal balance = state.getBalances().get(CurrencyLedgerState.accountKey(currency, account));
            return balance != null ? balance : BigDecimal.ZERO;
        });
    }

    @Override
    @NotNull
    public CompletionStage<CurrencyTransaction> transact(@NotNull CurrencyKey currency, @NotNull CurrencyDescriptor descriptor,
                                                         @NotNull CurrencyTransactionRequest request) {
        TransactJob job = new TransactJob(currency, descriptor, request);
        enqueue(job);
        return job.getResult();
    }

    @Override
    @NotNull
    public CompletionStage<CurrencyHistoryPage> history(@NotNull CurrencyKey currency, @NotNull CurrencyHistoryQuery query) {
        return async(() -> {
            List<CurrencyTransaction> filtered = new ArrayList<CurrencyTransaction>();
            Iterator<CurrencyTransaction> newestFirst = state.getTransactions().descendingIterator();
            while (newestFirst.hasNext()) {
                CurrencyTransaction transaction = newestFirst.next();
                if (HistoryFilter.matches(transaction, currency, query)) {
                    filtered.add(transaction);
                }
            }
            int from = Math.min(query.getOffset(), filtered.size());
            int to = (int) Math.min((long) from + query.getLimit(), filtered.size());
            return new CurrencyHistoryPage(new ArrayList<CurrencyTransaction>(filtered.subList(from, to)), query.getOffset(),
                    filtered.size() > query.getOffset() + query.getLimit());
        });
    }

    @Override
    @NotNull
    public CompletionStage<CurrencyStorageSnapshot> export(@NotNull CurrencyKey currency) {
        return async(() -> {
            String prefix = currency + "|";
            Map<UUID, BigDecimal> exportedBalances = new LinkedHashMap<UUID, BigDecimal>();
            for (Map.Entry<String, BigDecimal> entry : state.getBalances().entrySet()) {
                if (entry.getKey().startsWith(prefix)) {
                    exportedBalances.put(UUID.fromString(entry.getKey().substring(prefix.length())), entry.getValue());
                }
            }
            List<CurrencyTransaction> exportedTransactions = new ArrayList<CurrencyTransaction>();
            for (CurrencyTransaction transaction : state.getTransactions()) {
                if (transaction.getCurrency().equals(currency)) {
                    exportedTransactions.add(transaction);
                }
            }
            return new CurrencyStorageSnapshot(currency, Instant.now(), exportedBalances, exportedTransactions);
        });
    }

    @Override
    @NotNull
    public CompletionStage<CurrencyImportResult> importSnapshot(@NotNull CurrencyStorageSnapshot snapshot,
                                                                @NotNull CurrencyImportMode mode) {
        return async(() -> {
            for (CurrencyTransaction transaction : snapshot.getTransactions()) {
                if (!transaction.getCurrency().equals(snapshot.getCurrency())) {
                    throw new IllegalArgumentException("Snapshot contains transactions from another currency");
                }
            }
            return replacingState(() -> ledgerImport.apply(snapshot, mode));
        });
    }

    @Override
    public void close() {
        worker.close();
    }

    /** Applies {@code change} and saves; on any failure memory goes back to what the file still holds. */
    private <T> T replacingState(StorageAction<T> change) throws IOException {
        CurrencyLedgerState.Copy before = state.copy();
        try {
            T result = change.run();
            save();
            return result;
        } catch (Throwable error) {
            state.restore(before);
            throw error;
        }
    }

    /** Queues {@code job} for the worker; one drain run takes everything that waits, so jobs keep their arrival order. */
    private void enqueue(StorageJob job) {
        jobs.add(job);
        if (!drainScheduled.compareAndSet(false, true)) {
            return;
        }
        try {
            worker.submit(() -> {
                drain();
                return null;
            }).whenComplete((ignored, error) -> {
                if (error != null) {
                    failWaiting(error);
                }
            });
        } catch (RuntimeException | Error error) {
            drainScheduled.set(false);
            // A drain that already took the job will still answer it.
            if (jobs.remove(job)) {
                throw error;
            }
        }
    }

    private void drain() {
        drainScheduled.set(false);
        while (true) {
            StorageJob first = jobs.poll();
            if (first == null) {
                return;
            }
            if (first instanceof OtherJob) {
                ((OtherJob) first).run();
                continue;
            }
            List<TransactJob> batch = new ArrayList<TransactJob>();
            batch.add((TransactJob) first);
            while (batch.size() < MAXIMUM_BATCH && jobs.peek() instanceof TransactJob) {
                batch.add((TransactJob) jobs.poll());
            }
            commit(batch);
        }
    }

    /**
     * Applies {@code batch} in order and saves once; if that save fails, each transaction gets its own save so only the
     * failing ones fail.
     */
    private void commit(List<TransactJob> batch) {
        LedgerUndo undo = new LedgerUndo();
        List<TransactJob> waitingJobs = new ArrayList<TransactJob>();
        List<CurrencyTransaction> waitingTransactions = new ArrayList<CurrencyTransaction>();
        Set<UUID> unsaved = new LinkedHashSet<UUID>();
        for (TransactJob job : batch) {
            CurrencyTransaction replay = state.replay(job.getCurrency(), job.getRequest());
            if (replay != null && !unsaved.contains(replay.getId())) {
                job.finish(replay);
                continue;
            }
            int mark = undo.mark();
            CurrencyTransaction transaction;
            try {
                transaction = replay != null ? replay
                        : state.apply(job.getCurrency(), job.getDescriptor(), job.getRequest(), maximumTransactions, undo);
            } catch (Throwable error) {
                undo.revertTo(mark);
                job.fail(error);
                continue;
            }
            unsaved.add(transaction.getId());
            waitingJobs.add(job);
            waitingTransactions.add(transaction);
        }
        if (waitingJobs.isEmpty()) {
            return;
        }
        try {
            save();
        } catch (Throwable error) {
            undo.revert();
            if (waitingJobs.size() == 1) {
                waitingJobs.get(0).fail(error);
            } else {
                for (TransactJob job : waitingJobs) {
                    commit(Collections.singletonList(job));
                }
            }
            return;
        }
        for (int index = 0; index < waitingJobs.size(); index++) {
            waitingJobs.get(index).finish(waitingTransactions.get(index));
        }
    }

    /** Fails everything still queued when the worker gives up, so no caller waits forever. */
    private void failWaiting(Throwable error) {
        drainScheduled.set(false);
        for (StorageJob job = jobs.poll(); job != null; job = jobs.poll()) {
            job.fail(error);
        }
    }

    // Balances are money: the file must reach the disk before it replaces the previous one, or a power loss
    // after the rename can leave an empty file.
    private void save() throws IOException {
        if (Files.isSymbolicLink(file)) {
            throw new IllegalArgumentException("Currency storage file must not be a symbolic link");
        }
        replaceFile.replace(file, temporary -> {
            try (Writer out = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writer.write(out, state);
            } catch (IOException error) {
                throw new UncheckedIOException(error);
            }
        });
    }

    /** Queues {@code operation} on the single storage worker so file access never blocks the caller. */
    private <T> CompletionStage<T> async(StorageAction<T> operation) {
        CompletableFuture<T> result = new CompletableFuture<T>();
        enqueue(new OtherJob(() -> {
            try {
                result.complete(operation.run());
            } catch (Throwable error) {
                result.completeExceptionally(error);
            }
        }, result::completeExceptionally));
        return result;
    }
}
