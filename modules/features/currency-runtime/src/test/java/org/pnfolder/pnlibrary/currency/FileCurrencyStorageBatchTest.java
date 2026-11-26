package org.pnfolder.pnlibrary.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount;
import org.pnfolder.pnlibrary.api.currency.CurrencyActor;
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor;
import org.pnfolder.pnlibrary.api.currency.CurrencyHistoryQuery;
import org.pnfolder.pnlibrary.api.currency.CurrencyKey;
import org.pnfolder.pnlibrary.api.currency.CurrencyStorageSnapshot;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionRequest;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionType;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.common.io.AtomicFiles;

class FileCurrencyStorageBatchTest {
    private static final String GOLDEN = "b2d6d39db3fda0f3a53ba7c27164e601886a2b5ad36b8f117abdb25c69c59d54";
    private static final String GOLDEN_FAILING = "66c00db1263b8b105cfe6a9a3a73e6f4fd76d3c1f1f355d321a7831f94647e14";

    private final CurrencyKey currency = new CurrencyKey(PluginId.of("test"), "coins");
    private final CurrencyDescriptor descriptor = new CurrencyDescriptor("Coins", "C", 2, RoundingMode.DOWN);
    private final List<CurrencyAccount> accounts = new ArrayList<CurrencyAccount>();

    FileCurrencyStorageBatchTest() {
        for (int index = 1; index <= 5; index++) {
            accounts.add(new CurrencyAccount(new UUID(0, index)));
        }
    }

    private List<CurrencyTransactionRequest> corpus(int count) {
        Random random = new Random(42);
        CurrencyTransactionType[] types = CurrencyTransactionType.values();
        List<CurrencyTransactionRequest> requests = new ArrayList<CurrencyTransactionRequest>();
        for (int index = 0; index < count; index++) {
            CurrencyTransactionType type = types[random.nextInt(types.length)];
            BigDecimal amount;
            if (type == CurrencyTransactionType.RESET) {
                amount = BigDecimal.ZERO;
            } else if (index % 13 == 0) {
                amount = new BigDecimal("1.005");
            } else {
                amount = new BigDecimal(random.nextInt(900) + 1).movePointLeft(2);
            }
            CurrencyAccount first = accounts.get(random.nextInt(accounts.size()));
            CurrencyAccount second = accounts.get(random.nextInt(accounts.size()));
            CurrencyAccount source = type == CurrencyTransactionType.DEBIT || type == CurrencyTransactionType.TRANSFER ? first : null;
            CurrencyAccount target = type == CurrencyTransactionType.TRANSFER ? second
                    : type == CurrencyTransactionType.DEBIT ? null : first;
            String key = index % 7 == 0 ? "key-" + random.nextInt(20) : null;
            requests.add(new CurrencyTransactionRequest(type, amount, source, target, CurrencyActor.system("test"), "batch-test",
                    null, Collections.<String, String>emptyMap(), key));
        }
        return requests;
    }

    private static String describe(CurrencyTransaction transaction) {
        return String.join("|",
                String.valueOf(transaction.getType()), String.valueOf(transaction.getStatus()), String.valueOf(transaction.getAmount()),
                String.valueOf(transaction.getSource() == null ? null : transaction.getSource().getPlayerId()),
                String.valueOf(transaction.getTarget() == null ? null : transaction.getTarget().getPlayerId()),
                String.valueOf(transaction.getSourceBalanceBefore()), String.valueOf(transaction.getSourceBalanceAfter()),
                String.valueOf(transaction.getTargetBalanceBefore()), String.valueOf(transaction.getTargetBalanceAfter()),
                String.valueOf(transaction.getFailure()), String.valueOf(transaction.getIdempotencyKey()));
    }

    private String state(FileCurrencyStorage storage) {
        CurrencyStorageSnapshot snapshot = storage.export(currency).toCompletableFuture().join();
        List<Map.Entry<UUID, BigDecimal>> entries = new ArrayList<Map.Entry<UUID, BigDecimal>>(snapshot.getBalances().entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<UUID, BigDecimal>>() {
            @Override
            public int compare(Map.Entry<UUID, BigDecimal> a, Map.Entry<UUID, BigDecimal> b) {
                return a.getKey().compareTo(b.getKey());
            }
        });
        List<String> balances = new ArrayList<String>();
        for (Map.Entry<UUID, BigDecimal> entry : entries) {
            balances.add(entry.getKey() + "=" + entry.getValue());
        }
        List<String> lines = new ArrayList<String>();
        for (CurrencyTransaction transaction : snapshot.getTransactions()) {
            lines.add(describe(transaction));
        }
        return String.join(",", balances) + "\n" + String.join("\n", lines);
    }

    private static String sha(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static String outcome(CompletableFuture<CurrencyTransaction> future) {
        try {
            return describe(future.join());
        } catch (CompletionException error) {
            return "failed:" + (error.getCause() == null ? null : error.getCause().getMessage());
        }
    }

    private String run(Path file, boolean queued, FileReplacer writer) {
        FileCurrencyStorage storage = writer == null ? new FileCurrencyStorage(file, 100) : new FileCurrencyStorage(file, 100, writer);
        try {
            List<CompletableFuture<CurrencyTransaction>> futures = new ArrayList<CompletableFuture<CurrencyTransaction>>();
            StringBuilder log = new StringBuilder();
            for (CurrencyTransactionRequest request : corpus(400)) {
                CompletableFuture<CurrencyTransaction> future = storage.transact(currency, descriptor, request).toCompletableFuture();
                if (queued) {
                    futures.add(future);
                } else {
                    log.append(outcome(future)).append('\n');
                }
            }
            for (CompletableFuture<CurrencyTransaction> future : futures) {
                log.append(outcome(future)).append('\n');
            }
            log.append(state(storage));
            return log.toString();
        } finally {
            storage.close();
        }
    }

    @Test
    void corpusGivesTheSameLedgerWhenSequentialOrQueued(@TempDir Path directory) {
        String sequential = run(directory.resolve("a.json"), false, null);
        String queued = run(directory.resolve("b.json"), true, null);
        assertEquals(sequential, queued);
        assertEquals(GOLDEN, sha(sequential));
    }

    @Test
    void corpusWithFailingWritesGivesThePinnedResult(@TempDir Path directory) {
        AtomicInteger calls = new AtomicInteger();
        List<Integer> failing = java.util.Arrays.asList(3, 7, 8, 100);
        String result = run(directory.resolve("c.json"), false, (target, fill) -> {
            if (failing.contains(calls.incrementAndGet())) {
                throw new IllegalStateException("disk full");
            }
            AtomicFiles.write(target, true, fill);
        });
        assertEquals(GOLDEN_FAILING, sha(result));
    }

    @Test
    void reloadingTheFileGivesTheSameBalancesAndHistory(@TempDir Path directory) {
        Path file = directory.resolve("d.json");
        FileCurrencyStorage storage = new FileCurrencyStorage(file, 100);
        String before;
        try {
            for (CurrencyTransactionRequest request : corpus(400)) {
                storage.transact(currency, descriptor, request).toCompletableFuture().join();
            }
            before = state(storage);
        } finally {
            storage.close();
        }
        FileCurrencyStorage reloaded = new FileCurrencyStorage(file, 100);
        try {
            assertEquals(before, state(reloaded));
        } finally {
            reloaded.close();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(10, TimeUnit.SECONDS));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(error);
        }
    }

    @Test
    void aThousandParallelTransactionsAreSavedInAFewBatches(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("e.json");
        AtomicInteger saves = new AtomicInteger();
        AtomicLong written = new AtomicLong();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        FileCurrencyStorage storage = new FileCurrencyStorage(file, 100_000, (target, fill) -> {
            if (saves.incrementAndGet() == 1) {
                entered.countDown();
                await(release);
            }
            AtomicFiles.write(target, false, fill);
            written.addAndGet(Files.size(target));
        });
        try {
            CurrencyTransactionRequest credit = credit("1", null);
            List<CompletableFuture<CurrencyTransaction>> futures = new ArrayList<CompletableFuture<CurrencyTransaction>>();
            futures.add(storage.transact(currency, descriptor, credit).toCompletableFuture());
            await(entered);
            for (int index = 0; index < 999; index++) {
                futures.add(storage.transact(currency, descriptor, credit).toCompletableFuture());
            }
            release.countDown();
            for (CompletableFuture<CurrencyTransaction> future : futures) {
                future.join();
            }
            assertEquals(new BigDecimal("1000.00"), storage.balance(currency, accounts.get(0)).toCompletableFuture().join());
        } finally {
            storage.close();
        }
        long finalSize = Files.size(file);
        assertTrue(saves.get() <= 10, "saves=" + saves.get());
        assertTrue(written.get() <= 10 * finalSize, "written=" + written.get() + " final=" + finalSize);
    }

    @Test
    void aFailedBatchSaveKeepsBalancesAndIdempotencyAndLaterTransactionsStillWork(@TempDir Path directory) {
        AtomicBoolean broken = new AtomicBoolean(false);
        AtomicBoolean blockNext = new AtomicBoolean(false);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        FileCurrencyStorage storage = new FileCurrencyStorage(directory.resolve("f.json"), 100,
                gate(broken, blockNext, entered, release));
        try {
            storage.transact(currency, descriptor, credit("5", "ok")).toCompletableFuture().join();
            blockNext.set(true);
            CompletableFuture<CurrencyTransaction> blocker = storage.transact(currency, descriptor, credit("1", null)).toCompletableFuture();
            await(entered);
            broken.set(true);
            List<CompletableFuture<CurrencyTransaction>> doomed = new ArrayList<CompletableFuture<CurrencyTransaction>>();
            for (int n = 1; n <= 5; n++) {
                doomed.add(storage.transact(currency, descriptor, credit("10", "doomed-" + n)).toCompletableFuture());
            }
            release.countDown();
            blocker.join();
            for (CompletableFuture<CurrencyTransaction> future : doomed) {
                assertThrows(CompletionException.class, future::join);
            }
            assertEquals(0, new BigDecimal("6").compareTo(storage.balance(currency, accounts.get(0)).toCompletableFuture().join()));
            assertEquals(2, storage.history(currency, new CurrencyHistoryQuery()).toCompletableFuture().join().getItems().size());
            broken.set(false);
            CurrencyTransaction retried = storage.transact(currency, descriptor, credit("10", "doomed-1")).toCompletableFuture().join();
            assertEquals(0, new BigDecimal("16").compareTo(retried.getTargetBalanceAfter()));
        } finally {
            storage.close();
        }
    }

    @Test
    void aFailedBatchBringsBackEvictedHistoryAndItsIdempotencyKeys(@TempDir Path directory) {
        AtomicBoolean broken = new AtomicBoolean(false);
        AtomicBoolean blockNext = new AtomicBoolean(false);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        FileCurrencyStorage storage = new FileCurrencyStorage(directory.resolve("g.json"), 100,
                gate(broken, blockNext, entered, release));
        try {
            for (int n = 0; n < 99; n++) {
                storage.transact(currency, descriptor, credit("1", "old-" + n)).toCompletableFuture().join();
            }
            blockNext.set(true);
            CompletableFuture<CurrencyTransaction> blocker = storage.transact(currency, descriptor, credit("0.01", null)).toCompletableFuture();
            await(entered);
            broken.set(true);
            List<CompletableFuture<CurrencyTransaction>> doomed = new ArrayList<CompletableFuture<CurrencyTransaction>>();
            for (int n = 1; n <= 30; n++) {
                doomed.add(storage.transact(currency, descriptor, credit("2", "new-" + n)).toCompletableFuture());
            }
            release.countDown();
            blocker.join();
            for (CompletableFuture<CurrencyTransaction> future : doomed) {
                assertThrows(CompletionException.class, future::join);
            }
            broken.set(false);
            CurrencyTransaction replayed = storage.transact(currency, descriptor, credit("5", "old-0")).toCompletableFuture().join();
            assertEquals(0, new BigDecimal("1").compareTo(replayed.getAmount()));
            assertEquals(100, storage.history(currency, CurrencyHistoryQuery.builder().limit(500).build())
                    .toCompletableFuture().join().getItems().size());
            assertEquals(0, new BigDecimal("99.01").compareTo(storage.balance(currency, accounts.get(0)).toCompletableFuture().join()));
        } finally {
            storage.close();
        }
    }

    /** A writer that can be told to wait once and to fail on every save while it is broken. */
    private static FileReplacer gate(AtomicBoolean broken, AtomicBoolean blockNext, CountDownLatch entered, CountDownLatch release) {
        return (target, fill) -> {
            boolean failed = broken.get();
            if (blockNext.compareAndSet(true, false)) {
                entered.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
            }
            if (failed) {
                throw new IllegalStateException("disk full");
            }
            AtomicFiles.write(target, false, fill);
        };
    }

    private CurrencyTransactionRequest credit(String amount, String key) {
        return new CurrencyTransactionRequest(CurrencyTransactionType.CREDIT, new BigDecimal(amount), null, accounts.get(0),
                CurrencyActor.system("test"), "batch-test", null, Collections.<String, String>emptyMap(), key);
    }
}
