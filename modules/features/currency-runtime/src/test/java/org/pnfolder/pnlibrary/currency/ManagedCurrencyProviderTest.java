package org.pnfolder.pnlibrary.currency;

import org.pnfolder.pnlibrary.currency.storage.FileCurrencyStorage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount;
import org.pnfolder.pnlibrary.api.currency.CurrencyActor;
import org.pnfolder.pnlibrary.api.currency.CurrencyCommandSettings;
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor;
import org.pnfolder.pnlibrary.api.currency.CurrencyHistoryPage;
import org.pnfolder.pnlibrary.api.currency.CurrencyHistoryQuery;
import org.pnfolder.pnlibrary.api.currency.CurrencyImportMode;
import org.pnfolder.pnlibrary.api.currency.CurrencyImportResult;
import org.pnfolder.pnlibrary.api.currency.CurrencyKey;
import org.pnfolder.pnlibrary.api.currency.CurrencyRejectReason;
import org.pnfolder.pnlibrary.api.currency.CurrencyResult;
import org.pnfolder.pnlibrary.api.currency.CurrencyStorage;
import org.pnfolder.pnlibrary.api.currency.CurrencyStorageSnapshot;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionRequest;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionStatus;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionType;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

class ManagedCurrencyProviderTest {
    @Test
    void invalidTransferIsRejectedBeforeStorageInvocation() {
        RecordingStorage storage = new RecordingStorage();
        ManagedCurrencyProvider provider = provider(storage, false);
        CurrencyAccount account = new CurrencyAccount(UUID.randomUUID());
        CurrencyTransactionRequest request = request(CurrencyTransactionType.TRANSFER, "1.00", account, account);

        CurrencyTransaction result = provider.transact(request).toCompletableFuture().join();

        assertEquals(CurrencyTransactionStatus.REJECTED, result.getStatus());
        assertEquals("Transfer source and target must differ", result.getFailure());
        assertFalse(storage.transacted);
    }

    @Test
    void amountWithExcessPrecisionIsRejectedBeforeStorageInvocation() {
        RecordingStorage storage = new RecordingStorage();
        CurrencyTransaction result = provider(storage, false).transact(
                request(CurrencyTransactionType.CREDIT, "1.001", null, new CurrencyAccount(UUID.randomUUID())))
                .toCompletableFuture().join();

        assertEquals(CurrencyTransactionStatus.REJECTED, result.getStatus());
        assertFalse(storage.transacted);
    }

    @Test
    void ownedStorageIsClosedWithProvider() {
        RecordingStorage storage = new RecordingStorage();
        provider(storage, true).close();
        assertTrue(storage.closed);
    }

    @Test
    void withdrawingMoreThanTheBalanceReportsInsufficientFunds(@TempDir Path directory) throws Exception {
        try (FileCurrencyStorage storage = new FileCurrencyStorage(directory.resolve("currency.json"), 100)) {
            CurrencyResult result = provider(storage, false)
                    .withdraw(new CurrencyAccount(UUID.randomUUID()), BigDecimal.ONE).toCompletableFuture().join();

            assertEquals(CurrencyRejectReason.INSUFFICIENT_FUNDS, result.getReason());
        }
    }

    private static ManagedCurrencyProvider provider(CurrencyStorage storage, boolean ownsStorage) {
        return new ManagedCurrencyProvider(new CurrencyKey(PluginId.of("test"), "coins"),
                new CurrencyDescriptor("Coins", "C", 2, RoundingMode.DOWN), storage, "test", ownsStorage,
                new CurrencyCommandSettings());
    }

    private static CurrencyTransactionRequest request(CurrencyTransactionType type, String amount, CurrencyAccount source,
                                                      CurrencyAccount target) {
        return new CurrencyTransactionRequest(type, new BigDecimal(amount), source, target, CurrencyActor.system("test"), "test");
    }

    private static final class RecordingStorage implements CurrencyStorage {
        boolean transacted;
        boolean closed;

        @Override
        @NotNull
        public CompletionStage<BigDecimal> balance(@NotNull CurrencyKey currency, @NotNull CurrencyAccount account) {
            return CompletableFuture.completedFuture(BigDecimal.ZERO);
        }

        @Override
        @NotNull
        public CompletionStage<CurrencyTransaction> transact(@NotNull CurrencyKey currency, @NotNull CurrencyDescriptor descriptor,
                                                             @NotNull CurrencyTransactionRequest request) {
            transacted = true;
            return CompletableFuture.completedFuture(new CurrencyTransaction(UUID.randomUUID(), currency, request.getType(),
                    CurrencyTransactionStatus.COMMITTED, request.getAmount(), request.getSource(), request.getTarget(),
                    request.getActor(), request.getService(), request.getReason(), request.getMetadata(), null, null, null,
                    null, Instant.now(), null, null));
        }

        @Override
        @NotNull
        public CompletionStage<CurrencyHistoryPage> history(@NotNull CurrencyKey currency, @NotNull CurrencyHistoryQuery query) {
            return CompletableFuture.completedFuture(
                    new CurrencyHistoryPage(Collections.emptyList(), query.getOffset(), false));
        }

        @Override
        @NotNull
        public CompletionStage<CurrencyStorageSnapshot> export(@NotNull CurrencyKey currency) {
            return CompletableFuture.completedFuture(new CurrencyStorageSnapshot(currency, Instant.now(),
                    Collections.emptyMap(), Collections.emptyList()));
        }

        @Override
        @NotNull
        public CompletionStage<CurrencyImportResult> importSnapshot(@NotNull CurrencyStorageSnapshot snapshot,
                                                                    @NotNull CurrencyImportMode mode) {
            return CompletableFuture.completedFuture(new CurrencyImportResult(snapshot.getCurrency(), mode, 0, 0, 0, 0));
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
