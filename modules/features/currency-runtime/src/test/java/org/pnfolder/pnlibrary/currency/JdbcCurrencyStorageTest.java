package org.pnfolder.pnlibrary.currency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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

/** Runs the JDBC storage against an in-memory H2 database in MySQL mode, the dialect servers use most. */
class JdbcCurrencyStorageTest {
    private final JdbcDataSource source = new JdbcDataSource();
    private final JdbcCurrencyStorage storage;
    private final CurrencyKey currency = new CurrencyKey(PluginId.of("test"), "coins");
    private final CurrencyDescriptor descriptor = new CurrencyDescriptor("Coins", "C", 2, RoundingMode.DOWN);
    private final CurrencyAccount account = new CurrencyAccount(UUID.randomUUID());

    JdbcCurrencyStorageTest() {
        source.setURL("jdbc:h2:mem:currency-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        storage = new JdbcCurrencyStorage(source, "pn_currency");
    }

    @AfterEach
    void close() {
        storage.close();
    }

    @Test
    void creditsAndDebitsUpdateTheBalanceAndTheLedger() {
        transact(CurrencyTransactionType.CREDIT, "12.50", "credit-1");
        transact(CurrencyTransactionType.DEBIT, "2.25", "debit-1");

        assertEquals(0, new BigDecimal("10.25").compareTo(join(storage.balance(currency, account))));
        CurrencyHistoryPage page = join(storage.history(currency, new CurrencyHistoryQuery()));
        assertEquals(2, page.getItems().size());
        Set<String> keys = new HashSet<String>();
        for (CurrencyTransaction transaction : page.getItems()) {
            keys.add(transaction.getIdempotencyKey());
        }
        assertEquals(new HashSet<String>(Arrays.asList("credit-1", "debit-1")), keys);
        assertFalse(page.getHasMore());
    }

    @Test
    void aRepeatedIdempotencyKeyReturnsTheFirstTransactionAndChangesNothing() {
        CurrencyTransaction first = transact(CurrencyTransactionType.CREDIT, "5", "once");
        CurrencyTransaction repeated = transact(CurrencyTransactionType.CREDIT, "5", "once");

        assertEquals(first.getId(), repeated.getId());
        assertEquals(0, new BigDecimal("5").compareTo(join(storage.balance(currency, account))));
        assertEquals(1, join(storage.history(currency, new CurrencyHistoryQuery())).getItems().size());
    }

    @Test
    void anExportRestoresIntoAnEmptyStoreWithTheSameBalancesAndLedger() {
        transact(CurrencyTransactionType.CREDIT, "7.5", "credit-1");
        CurrencyStorageSnapshot snapshot = join(storage.export(currency));

        JdbcDataSource targetSource = new JdbcDataSource();
        targetSource.setURL("jdbc:h2:mem:currency-target-" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        JdbcCurrencyStorage target = new JdbcCurrencyStorage(targetSource, "pn_currency");
        try {
            CurrencyImportResult result = join(target.importSnapshot(snapshot, CurrencyImportMode.REPLACE));
            assertEquals(1, result.getImportedAccounts());
            assertEquals(1, result.getImportedTransactions());
            assertEquals(0, new BigDecimal("7.5").compareTo(join(target.balance(currency, account))));
            assertTrue(join(target.history(currency, new CurrencyHistoryQuery())).getItems().get(0).getStatus()
                    == CurrencyTransactionStatus.COMMITTED);
        } finally {
            target.close();
        }
    }

    @Test
    void borrowedConnectionsGoBackWithTheirOriginalTransactionSettings() throws Exception {
        List<Connection> borrowed = new ArrayList<Connection>();
        DataSource tracking = (DataSource) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {DataSource.class},
                (proxy, method, args) -> {
                    Object result;
                    try {
                        result = method.invoke(source, args == null ? new Object[0] : args);
                    } catch (InvocationTargetException error) {
                        throw error.getTargetException();
                    }
                    if (!method.getName().equals("getConnection")) {
                        return result;
                    }
                    Connection real = (Connection) result;
                    // Keep the connection open after "close" so its state can be inspected, as a pool would.
                    InvocationHandler keepOpen = (connectionProxy, call, callArgs) -> {
                        if (call.getName().equals("close")) {
                            return null;
                        }
                        try {
                            return call.invoke(real, callArgs == null ? new Object[0] : callArgs);
                        } catch (InvocationTargetException error) {
                            throw error.getTargetException();
                        }
                    };
                    Connection kept = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
                            new Class<?>[] {Connection.class}, keepOpen);
                    borrowed.add(kept);
                    return kept;
                });

        JdbcCurrencyStorage pooled = new JdbcCurrencyStorage(tracking, "pn_currency_pooled");
        try {
            join(pooled.transact(currency, descriptor, new CurrencyTransactionRequest(CurrencyTransactionType.CREDIT,
                    new BigDecimal("1"), null, account, CurrencyActor.system("test"), "test-suite", "unit-test",
                    Collections.<String, String>emptyMap(), "pooled-1")));
            join(pooled.export(currency));
        } finally {
            pooled.close();
        }

        assertFalse(borrowed.isEmpty());
        for (Connection connection : borrowed) {
            assertTrue(connection.getAutoCommit());
            assertEquals(Connection.TRANSACTION_READ_COMMITTED, connection.getTransactionIsolation());
        }
    }

    private CurrencyTransaction transact(CurrencyTransactionType type, String amount, String key) {
        return join(storage.transact(currency, descriptor, new CurrencyTransactionRequest(type, new BigDecimal(amount),
                type == CurrencyTransactionType.DEBIT ? account : null, type == CurrencyTransactionType.CREDIT ? account : null,
                CurrencyActor.system("test"), "test-suite", "unit-test", Collections.<String, String>emptyMap(), key)));
    }

    private static <T> T join(CompletionStage<T> stage) {
        return stage.toCompletableFuture().join();
    }
}
