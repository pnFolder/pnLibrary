package org.pnfolder.pnlibrary.currency;

import java.util.Locale;
import org.pnfolder.pnlibrary.currency.storage.CurrencyLedgerState;
import org.pnfolder.pnlibrary.currency.storage.FileCurrencyStorage;
import org.pnfolder.pnlibrary.currency.storage.CurrencyTransactionJson;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount;
import org.pnfolder.pnlibrary.api.currency.CurrencyActor;
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor;
import org.pnfolder.pnlibrary.api.currency.CurrencyHistoryQuery;
import org.pnfolder.pnlibrary.api.currency.CurrencyImportMode;
import org.pnfolder.pnlibrary.api.currency.CurrencyKey;
import org.pnfolder.pnlibrary.api.currency.CurrencyStorageSnapshot;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionRequest;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionType;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.common.io.AtomicFiles;

class CurrencyFileBytesTest {
    private final CurrencyKey currency = new CurrencyKey(PluginId.of("test"), "coins");
    private final CurrencyDescriptor descriptor = new CurrencyDescriptor("Coins", "C", 2, RoundingMode.DOWN);
    private final List<CurrencyAccount> accounts = new ArrayList<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final List<String> reasons = Arrays.asList(null, "plain",
            "quote " + (char) 34 + " slash " + (char) 92 + " tab" + (char) 9 + " line" + (char) 10 + "break",
            "<b>&'</b> é 日本 😀 " + (char) 8232, "");

    CurrencyFileBytesTest() {
        for (int index = 1; index <= 5; index++) {
            accounts.add(new CurrencyAccount(new UUID(0, index)));
        }
    }

    private List<CurrencyTransactionRequest> requests(int count) {
        Random random = new Random(7);
        CurrencyTransactionType[] types = CurrencyTransactionType.values();
        List<CurrencyTransactionRequest> requests = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            CurrencyTransactionType type = types[random.nextInt(types.length)];
            CurrencyAccount first = accounts.get(random.nextInt(accounts.size()));
            CurrencyAccount second = accounts.get(random.nextInt(accounts.size()));
            BigDecimal amount = type == CurrencyTransactionType.RESET ? BigDecimal.ZERO
                    : new BigDecimal(random.nextInt(900) + 1).movePointLeft(2);
            CurrencyAccount source = type == CurrencyTransactionType.DEBIT || type == CurrencyTransactionType.TRANSFER ? first : null;
            CurrencyAccount target = type == CurrencyTransactionType.TRANSFER ? second
                    : type == CurrencyTransactionType.DEBIT ? null : first;
            Map<String, String> metadata = new LinkedHashMap<>();
            if (index % 3 == 0) {
                metadata.put("k" + index, "v \"" + index + "\"");
                metadata.put("é", "<&>");
            }
            String key = index % 7 == 0 ? "key-" + random.nextInt(20) : null;
            requests.add(new CurrencyTransactionRequest(type, amount, source, target, CurrencyActor.system("test"), "bytes-test",
                    reasons.get(index % reasons.size()), metadata, key));
        }
        return requests;
    }

    /** Reference serialization of the whole tree without caching encoded transactions. */
    private byte[] referenceBytes(FileCurrencyStorage storage) {
        CurrencyStorageSnapshot snapshot = storage.export(currency).toCompletableFuture().join();
        JsonObject root = new JsonObject();
        JsonObject balances = new JsonObject();
        for (Map.Entry<UUID, BigDecimal> entry : snapshot.getBalances().entrySet()) {
            balances.addProperty(CurrencyLedgerState.accountKey(currency, new CurrencyAccount(entry.getKey())),
                    entry.getValue().toPlainString());
        }
        root.add("balances", balances);
        Set<UUID> history = new HashSet<>();
        for (CurrencyTransaction transaction : storage.history(currency, CurrencyHistoryQuery.builder().limit(500).build())
                .toCompletableFuture().join().getItems()) {
            history.add(transaction.getId());
        }
        JsonArray transactions = new JsonArray();
        JsonArray receipts = new JsonArray();
        for (CurrencyTransaction transaction : snapshot.getTransactions()) {
            (history.contains(transaction.getId()) ? transactions : receipts).add(CurrencyTransactionJson.encode(transaction));
        }
        root.add("transactions", transactions);
        if (receipts.size() > 0) {
            root.add("archivedReceipts", receipts);
        }
        return gson.toJson(root).getBytes(StandardCharsets.UTF_8);
    }

    private static String sha(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder();
            for (byte value : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    @Test
    void theFileIsByteForByteWhatTheWholeTreeWriterGivesWithTrimmingAndFailedSaves(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("ledger.json");
        AtomicInteger calls = new AtomicInteger();
        List<Integer> failing = Arrays.asList(5, 6, 90, 91, 200);
        int compared = 0;
        FileCurrencyStorage storage = new FileCurrencyStorage(file, 100, (target, fill) -> {
            if (failing.contains(calls.incrementAndGet())) {
                throw new IllegalStateException("disk full");
            }
            AtomicFiles.write(target, false, fill);
        });
        try {
            List<CurrencyTransactionRequest> requests = requests(400);
            for (int index = 0; index < requests.size(); index++) {
                try {
                    storage.transact(currency, descriptor, requests.get(index)).toCompletableFuture().join();
                } catch (RuntimeException expected) {
                    // a failed save is part of the scenario
                }
                if (index % 5 == 0 && Files.exists(file)) {
                    assertEquals(sha(referenceBytes(storage)), sha(Files.readAllBytes(file)), "after request " + index);
                    compared++;
                }
            }
            assertEquals(sha(referenceBytes(storage)), sha(Files.readAllBytes(file)));
        } finally {
            storage.close();
        }
        assertTrue(compared > 70);
    }

    @Test
    void anEmptiedLedgerAndAReloadedFileKeepTheSameBytes(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("ledger.json");
        FileCurrencyStorage storage = new FileCurrencyStorage(file, 1000);
        try {
            for (CurrencyTransactionRequest request : requests(60)) {
                storage.transact(currency, descriptor, request).toCompletableFuture().join();
            }
            CurrencyStorageSnapshot snapshot = storage.export(currency).toCompletableFuture().join();
            storage.importSnapshot(snapshot.with(Collections.emptyMap(), Collections.emptyList()),
                    CurrencyImportMode.REPLACE).toCompletableFuture().join();
            assertEquals(sha(referenceBytes(storage)), sha(Files.readAllBytes(file)));
            assertTrue(new String(Files.readAllBytes(file), StandardCharsets.UTF_8).contains("\"transactions\": []"));
            storage.importSnapshot(snapshot, CurrencyImportMode.REPLACE).toCompletableFuture().join();
            assertEquals(sha(referenceBytes(storage)), sha(Files.readAllBytes(file)));
        } finally {
            storage.close();
        }
        byte[] reloaded = Files.readAllBytes(file);
        FileCurrencyStorage reopened = new FileCurrencyStorage(file, 1000);
        try {
            reopened.transact(currency, descriptor, requests(1).get(0)).toCompletableFuture().join();
            assertEquals(sha(referenceBytes(reopened)), sha(Files.readAllBytes(file)));
        } finally {
            reopened.close();
        }
        assertTrue(reloaded.length > 0);
    }

    @Test
    void aThousandOperationsEncodeEachTransactionOnce(@TempDir Path directory) {
        AtomicLong encodings = new AtomicLong();
        List<Integer> ledgerSizes = new ArrayList<>();
        FileCurrencyStorage storage = new FileCurrencyStorage(directory.resolve("ledger.json"), 5000, () -> encodings.incrementAndGet(),
                (target, fill) -> {
                    AtomicFiles.write(target, false, fill);
                    ledgerSizes.add(1);
                });
        try {
            for (CurrencyTransactionRequest request : requests(1000)) {
                storage.transact(currency, descriptor, request).toCompletableFuture().join();
            }
            long ledger = storage.export(currency).toCompletableFuture().join().getTransactions().size();
            // before: each of the saves encoded the whole ledger so far; now each transaction once
            long old = 0;
            for (int save = 1; save <= ledgerSizes.size(); save++) {
                old += Math.min(save, ledger);
            }
            assertEquals(ledger, encodings.get());
            assertTrue(old > 100L * encodings.get(), "old=" + old + " new=" + encodings.get());
        } finally {
            storage.close();
        }
    }
}
