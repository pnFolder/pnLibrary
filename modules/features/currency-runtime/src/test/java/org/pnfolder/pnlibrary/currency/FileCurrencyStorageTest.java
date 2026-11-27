package org.pnfolder.pnlibrary.currency;

import org.pnfolder.pnlibrary.currency.storage.FileCurrencyStorage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount;
import org.pnfolder.pnlibrary.api.currency.CurrencyActor;
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor;
import org.pnfolder.pnlibrary.api.currency.CurrencyHistoryPage;
import org.pnfolder.pnlibrary.api.currency.CurrencyHistoryQuery;
import org.pnfolder.pnlibrary.api.currency.CurrencyImportMode;
import org.pnfolder.pnlibrary.api.currency.CurrencyImportResult;
import org.pnfolder.pnlibrary.api.currency.CurrencyKey;
import org.pnfolder.pnlibrary.api.currency.CurrencyStorageSnapshot;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionRequest;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionStatus;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionType;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

class FileCurrencyStorageTest {
    private final CurrencyKey currency = new CurrencyKey(PluginId.of("test"), "coins");
    private final CurrencyDescriptor descriptor = new CurrencyDescriptor("Coins", "C", 2, RoundingMode.DOWN);

    @Test
    void closePersistsQueuedTransactionsBeforeReturning(@TempDir Path directory) {
        Path file = directory.resolve("queued.json");
        CurrencyAccount account = new CurrencyAccount(UUID.randomUUID());
        FileCurrencyStorage storage = new FileCurrencyStorage(file, 100);
        List<CompletableFuture<CurrencyTransaction>> operations = new ArrayList<>();
        for (int index = 1; index <= 20; index++) {
            operations.add(storage.transact(currency, descriptor, new CurrencyTransactionRequest(CurrencyTransactionType.CREDIT,
                    BigDecimal.ONE, null, account, CurrencyActor.system("test"), "close-test")).toCompletableFuture());
        }
        storage.close();
        for (CompletableFuture<CurrencyTransaction> operation : operations) {
            assertEquals(CurrencyTransactionStatus.COMMITTED, operation.join().getStatus());
        }
        FileCurrencyStorage reopened = new FileCurrencyStorage(file, 100);
        try {
            assertEquals(new BigDecimal("20.00"), reopened.balance(currency, account).toCompletableFuture().join());
        } finally {
            reopened.close();
        }
    }

    @Test
    void transactionSurvivesCloseAndReopen(@TempDir Path directory) {
        Path file = directory.resolve("currency.json");
        CurrencyAccount account = new CurrencyAccount(UUID.randomUUID());
        CurrencyTransactionRequest request = new CurrencyTransactionRequest(CurrencyTransactionType.CREDIT, new BigDecimal("12.50"),
                null, account, CurrencyActor.system("test"), "test-suite", "round-trip",
                Collections.singletonMap("source", "unit-test"), "credit-1");

        FileCurrencyStorage first = new FileCurrencyStorage(file, 100);
        try {
            CurrencyTransaction transaction = first.transact(currency, descriptor, request).toCompletableFuture().join();
            assertEquals(CurrencyTransactionStatus.COMMITTED, transaction.getStatus());
        } finally {
            first.close();
        }

        FileCurrencyStorage second = new FileCurrencyStorage(file, 100);
        try {
            assertEquals(new BigDecimal("12.50"), second.balance(currency, account).toCompletableFuture().join());
            CurrencyHistoryPage page = second.history(currency, new CurrencyHistoryQuery()).toCompletableFuture().join();
            assertEquals(1, page.getItems().size());
            assertEquals("round-trip", page.getItems().get(0).getReason());
            assertEquals(Collections.singletonMap("source", "unit-test"), page.getItems().get(0).getMetadata());
            assertFalse(page.getHasMore());
        } finally {
            second.close();
        }
    }

    private CurrencyTransaction credit(FileCurrencyStorage storage, CurrencyAccount account, String amount, String key) {
        return storage.transact(currency, descriptor, new CurrencyTransactionRequest(CurrencyTransactionType.CREDIT,
                new BigDecimal(amount), null, account, CurrencyActor.system("test"), "file-test", null,
                Collections.emptyMap(), key)).toCompletableFuture().join();
    }

    private CurrencyTransaction credit(FileCurrencyStorage storage, CurrencyAccount account, String amount) {
        return credit(storage, account, amount, null);
    }

    private BigDecimal balanceOf(FileCurrencyStorage storage, CurrencyAccount account) {
        return storage.balance(currency, account).toCompletableFuture().join();
    }

    private CurrencyHistoryPage history(FileCurrencyStorage storage, CurrencyHistoryQuery query) {
        return storage.history(currency, query).toCompletableFuture().join();
    }

    @Test
    void aFailedWriteLeavesBalancesHistoryAndIdempotencyUntouched(@TempDir Path directory) throws IOException {
        Path folder = directory.resolve("data");
        CurrencyAccount account = new CurrencyAccount(UUID.randomUUID());
        FileCurrencyStorage storage = new FileCurrencyStorage(folder.resolve("currency.json"), 100);
        try {
            credit(storage, account, "5", "first");
            // The data folder becomes a regular file, so the next save cannot succeed.
            List<Path> paths = new ArrayList<>();
            try (Stream<Path> walk = Files.walk(folder)) {
                walk.forEach(paths::add);
            }
            Collections.sort(paths, Collections.reverseOrder());
            for (Path path : paths) {
                Files.delete(path);
            }
            Files.write(folder, new byte[] {0});

            assertThrows(CompletionException.class, () -> credit(storage, account, "7", "second"));

            assertEquals(0, new BigDecimal("5").compareTo(balanceOf(storage, account)));
            assertEquals(1, history(storage, new CurrencyHistoryQuery()).getItems().size());
            Files.delete(folder);
            // The failed key was forgotten, so it may be retried once storage works again.
            assertEquals(CurrencyTransactionStatus.COMMITTED, credit(storage, account, "7", "second").getStatus());
            assertEquals(0, new BigDecimal("12").compareTo(balanceOf(storage, account)));
        } finally {
            storage.close();
        }
    }

    @Test
    void oldReceiptsPreventDuplicateCreditsAfterEvictionReopenAndImport(@TempDir Path directory) {
        CurrencyAccount account = new CurrencyAccount(UUID.randomUUID());
        Path file = directory.resolve("currency.json");
        CurrencyTransaction original;
        try (FileCurrencyStorage storage = new FileCurrencyStorage(file, 100)) {
            original = credit(storage, account, "1", "oldest");
            for (int index = 0; index < 100; index++) {
                credit(storage, account, "1");
            }

            CurrencyHistoryPage page = history(storage, CurrencyHistoryQuery.builder().limit(500).build());
            assertEquals(100, page.getItems().size());
            assertFalse(keysOf(page).contains("oldest"));
            assertEquals(original, credit(storage, account, "1", "oldest"));
        }

        CurrencyStorageSnapshot snapshot;
        try (FileCurrencyStorage reopened = new FileCurrencyStorage(file, 100)) {
            assertEquals(original, credit(reopened, account, "1", "oldest"));
            assertEquals(0, new BigDecimal("101").compareTo(balanceOf(reopened, account)));
            snapshot = reopened.export(currency).toCompletableFuture().join();
        }
        try (FileCurrencyStorage imported = new FileCurrencyStorage(directory.resolve("imported.json"), 100)) {
            imported.importSnapshot(snapshot, CurrencyImportMode.REPLACE).toCompletableFuture().join();
            assertEquals(original, credit(imported, account, "1", "oldest"));
            assertEquals(0, new BigDecimal("101").compareTo(balanceOf(imported, account)));
        }
    }

    @Test
    void mergeKeepsExistingAccountsWhileReplaceDiscardsThem(@TempDir Path directory) {
        CurrencyAccount kept = new CurrencyAccount(UUID.randomUUID());
        CurrencyAccount added = new CurrencyAccount(UUID.randomUUID());
        FileCurrencyStorage source = new FileCurrencyStorage(directory.resolve("source.json"), 100);
        CurrencyStorageSnapshot snapshot;
        try {
            credit(source, kept, "50");
            credit(source, added, "3");
            snapshot = source.export(currency).toCompletableFuture().join();
        } finally {
            source.close();
        }

        FileCurrencyStorage merging = new FileCurrencyStorage(directory.resolve("merge.json"), 100);
        try {
            credit(merging, kept, "1");
            CurrencyImportResult result = merging.importSnapshot(snapshot, CurrencyImportMode.MERGE_KEEP_TARGET)
                    .toCompletableFuture().join();

            assertEquals(1, result.getImportedAccounts());
            assertEquals(1, result.getSkippedAccounts());
            assertEquals(0, new BigDecimal("1").compareTo(balanceOf(merging, kept)));
            assertEquals(0, new BigDecimal("3").compareTo(balanceOf(merging, added)));
        } finally {
            merging.close();
        }
        FileCurrencyStorage replacing = new FileCurrencyStorage(directory.resolve("replace.json"), 100);
        try {
            credit(replacing, kept, "1");
            replacing.importSnapshot(snapshot, CurrencyImportMode.REPLACE).toCompletableFuture().join();

            assertEquals(0, new BigDecimal("50").compareTo(balanceOf(replacing, kept)));
            assertEquals(snapshot.getTransactions().size(), history(replacing, new CurrencyHistoryQuery()).getItems().size());
        } finally {
            replacing.close();
        }
    }

    private static List<String> keysOf(CurrencyHistoryPage page) {
        List<String> keys = new ArrayList<>();
        for (CurrencyTransaction transaction : page.getItems()) {
            keys.add(transaction.getIdempotencyKey());
        }
        return keys;
    }

    @Test
    void historyIsFilteredByAccountAndPagedNewestFirst(@TempDir Path directory) {
        CurrencyAccount first = new CurrencyAccount(UUID.randomUUID());
        CurrencyAccount second = new CurrencyAccount(UUID.randomUUID());
        FileCurrencyStorage storage = new FileCurrencyStorage(directory.resolve("currency.json"), 100);
        try {
            for (int index = 0; index < 3; index++) {
                credit(storage, first, "1", "a" + index);
            }
            credit(storage, second, "1", "b0");

            CurrencyHistoryPage own = history(storage, CurrencyHistoryQuery.builder().account(first.getPlayerId()).build());
            assertEquals(Arrays.asList("a2", "a1", "a0"), keysOf(own));

            CurrencyHistoryPage page = history(storage, CurrencyHistoryQuery.builder().limit(2).offset(1).build());
            assertEquals(Arrays.asList("a2", "a1"), keysOf(page));
            assertTrue(page.getHasMore());
        } finally {
            storage.close();
        }
    }

    @Test
    void aCorruptStorageFileStopsTheFeatureInsteadOfStartingWithEmptyBalances(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("currency.json");
        Files.write(file, "{ not json".getBytes(StandardCharsets.UTF_8));

        assertThrows(RuntimeException.class, () -> new FileCurrencyStorage(file, 100));
    }
}
