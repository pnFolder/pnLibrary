package org.pnfolder.pnlibrary.currency;

import org.pnfolder.pnlibrary.currency.storage.JdbcCurrencyStorage;
import org.pnfolder.pnlibrary.currency.storage.FileCurrencyStorage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount;
import org.pnfolder.pnlibrary.api.currency.CurrencyActor;
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor;
import org.pnfolder.pnlibrary.api.currency.CurrencyHistoryQuery;
import org.pnfolder.pnlibrary.api.currency.CurrencyKey;
import org.pnfolder.pnlibrary.api.currency.CurrencyStorage;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionRequest;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionStatus;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionType;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

class CurrencyStorageValidationTest {
    @TempDir
    Path directory;

    private final CurrencyKey currency = new CurrencyKey(PluginId.of("test"), "coins");
    private final CurrencyDescriptor descriptor = new CurrencyDescriptor("Coins", "C", 2, RoundingMode.DOWN);
    private final CurrencyAccount account = new CurrencyAccount(UUID.randomUUID());

    @Test
    void directStorageRequestsCannotBypassValidationOrChangeBalances() throws Exception {
        for (String backend : Arrays.asList("file", "jdbc")) {
            try (CurrencyStorage storage = storage(backend)) {
                storage.transact(currency, descriptor, request(CurrencyTransactionType.CREDIT, "10", null, account))
                        .toCompletableFuture().join();
                List<CurrencyTransactionRequest> invalid = Arrays.asList(
                        request(CurrencyTransactionType.CREDIT, "-1", null, account),
                        request(CurrencyTransactionType.DEBIT, "-1", account, null),
                        request(CurrencyTransactionType.CREDIT, "0", null, account),
                        request(CurrencyTransactionType.CREDIT, "1.001", null, account),
                        request(CurrencyTransactionType.TRANSFER, "1", account, account),
                        request(CurrencyTransactionType.TRANSFER, "1", account, null),
                        request(CurrencyTransactionType.CREDIT, "1", null, null),
                        request(CurrencyTransactionType.DEBIT, "1", null, null),
                        request(CurrencyTransactionType.SET_BALANCE, "-1", null, account),
                        request(CurrencyTransactionType.RESET, "1", null, account));
                for (int index = 0; index < invalid.size(); index++) {
                    CurrencyTransactionRequest retryable = invalid.get(index).withIdempotencyKey("invalid-" + index);
                    CurrencyTransaction transaction = storage.transact(currency, descriptor, retryable).toCompletableFuture().join();
                    assertEquals(CurrencyTransactionStatus.REJECTED, transaction.getStatus(), backend + ": " + invalid.get(index));
                    assertFalse(transaction.getFailure() == null || transaction.getFailure().trim().isEmpty());
                    assertEquals(transaction.getId(),
                            storage.transact(currency, descriptor, retryable).toCompletableFuture().join().getId());
                    assertEquals(0, BigDecimal.TEN.compareTo(storage.balance(currency, account).toCompletableFuture().join()), backend);
                }
                assertEquals(invalid.size() + 1,
                        storage.history(currency, new CurrencyHistoryQuery()).toCompletableFuture().join().getItems().size());
            }
        }
    }

    @Test
    void unnecessaryRoundingRejectsExcessPrecisionWithoutThrowing() throws Exception {
        CurrencyDescriptor strict = new CurrencyDescriptor(descriptor.getDisplayName(), descriptor.getSymbol(),
                descriptor.getFractionDigits(), RoundingMode.UNNECESSARY);
        assertFalse(strict.accepts(new BigDecimal("1.001")));
        for (String backend : Arrays.asList("file", "jdbc")) {
            try (CurrencyStorage storage = storage(backend)) {
                CurrencyTransaction transaction = storage
                        .transact(currency, strict, request(CurrencyTransactionType.CREDIT, "1.001", null, account))
                        .toCompletableFuture().join();
                assertEquals(CurrencyTransactionStatus.REJECTED, transaction.getStatus(), backend);
                assertEquals(0, storage.balance(currency, account).toCompletableFuture().join().signum(), backend);
            }
        }
    }

    private CurrencyStorage storage(String backend) {
        if (backend.equals("file")) {
            return new FileCurrencyStorage(directory.resolve(UUID.randomUUID() + ".json"), 100);
        }
        JdbcDataSource source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:validation-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        return new JdbcCurrencyStorage(source, "pn_currency");
    }

    private static CurrencyTransactionRequest request(CurrencyTransactionType type, String amount, CurrencyAccount source,
                                                      CurrencyAccount target) {
        return new CurrencyTransactionRequest(type, new BigDecimal(amount), source, target, CurrencyActor.system("test"),
                "validation-test");
    }
}
