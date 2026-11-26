package org.pnfolder.pnlibrary.currency.storage;

import java.util.Objects;
import org.pnfolder.pnlibrary.currency.CurrencyMutationEngine;
import org.pnfolder.pnlibrary.currency.CurrencyMutation;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import javax.sql.DataSource;

import com.google.gson.Gson;
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
import org.pnfolder.pnlibrary.common.lang.Failures;

/**
 * JDBC currency storage: each balance change and its ledger row commit in one SERIALIZABLE transaction.
 * Row SQL lives in {@link JdbcCurrencyLedger}, history paging in {@link JdbcHistoryQuery}, snapshots in
 * {@link JdbcCurrencySnapshots}.
 */
public final class JdbcCurrencyStorage implements CurrencyStorage {
    private static final int MAX_ATTEMPTS = 3;

    private final DataSource dataSource;
    private final JdbcCurrencySchema schema;
    private final Object schemaLock = new Object();
    private volatile boolean initialized;
    private final JdbcCurrencyLedger ledger;
    private final JdbcHistoryQuery historyQuery;
    private final JdbcCurrencySnapshots snapshots;
    private final ManagedWorker worker = new ManagedWorker("pnLibrary-currency-jdbc", 2);

    /** A piece of JDBC work that may fail with an SQL error. */
    private interface SqlWork<T> {
        T run() throws SQLException;
    }

    /** A piece of JDBC work on a borrowed connection. */
    private interface ConnectionWork<T> {
        T run(Connection connection) throws SQLException;
    }

    public JdbcCurrencyStorage(@NotNull DataSource dataSource, @NotNull String tablePrefix) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.schema = new JdbcCurrencySchema(tablePrefix);
        Gson gson = new Gson();
        JdbcTransactionDecoder decoder = new JdbcTransactionDecoder(gson);
        this.ledger = new JdbcCurrencyLedger(schema, gson, decoder);
        this.historyQuery = new JdbcHistoryQuery(schema, decoder);
        this.snapshots = new JdbcCurrencySnapshots(schema, ledger, decoder);
    }

    @Override
    @NotNull
    public CompletionStage<BigDecimal> balance(@NotNull CurrencyKey currency, @NotNull CurrencyAccount account) {
        return async(() -> connection(connection -> ledger.readBalance(connection, currency, account)));
    }

    @Override
    @NotNull
    public CompletionStage<CurrencyTransaction> transact(@NotNull CurrencyKey currency, @NotNull CurrencyDescriptor descriptor,
                                                         @NotNull CurrencyTransactionRequest request) {
        return async(() -> retryOnConflict(() -> connection(connection ->
                committed(connection, Connection.TRANSACTION_SERIALIZABLE, () -> {
                    String key = request.getIdempotencyKey();
                    CurrencyTransaction known = key == null ? null : ledger.findIdempotent(connection, currency, key);
                    return known != null ? known : applyMutation(connection, currency, descriptor, request);
                }))));
    }

    /** Locks the affected accounts, evaluates {@code request} and writes balances and the ledger row. */
    private CurrencyTransaction applyMutation(Connection connection, CurrencyKey currency, CurrencyDescriptor descriptor,
                                              CurrencyTransactionRequest request) throws SQLException {
        // Rows are locked in one fixed order so two opposite transfers cannot deadlock each other.
        Map<UUID, CurrencyAccount> accounts = new LinkedHashMap<>();
        for (CurrencyAccount account : new CurrencyAccount[] {request.getSource(), request.getTarget()}) {
            if (account != null && !accounts.containsKey(account.getPlayerId())) {
                accounts.put(account.getPlayerId(), account);
            }
        }
        List<CurrencyAccount> ordered = new ArrayList<>(accounts.values());
        Collections.sort(ordered, Comparator.comparing((CurrencyAccount account) -> account.getPlayerId().toString()));
        Map<UUID, BigDecimal> balances = new LinkedHashMap<>();
        for (CurrencyAccount account : ordered) {
            balances.put(account.getPlayerId(), ledger.lockBalance(connection, currency, account));
        }
        BigDecimal sourceBefore = normalizedBalance(descriptor, balances, request.getSource());
        BigDecimal targetBefore = normalizedBalance(descriptor, balances, request.getTarget());
        CurrencyMutation mutation = CurrencyMutationEngine.evaluate(request, sourceBefore, targetBefore, descriptor);
        if (mutation.isCommitted()) {
            writeAfter(connection, currency, descriptor, request.getSource(), mutation.getSourceAfter());
            writeAfter(connection, currency, descriptor, request.getTarget(), mutation.getTargetAfter());
        }
        CurrencyTransaction transaction = mutation.transaction(currency, descriptor, request);
        ledger.insertTransaction(connection, transaction);
        return transaction;
    }

    private static BigDecimal normalizedBalance(CurrencyDescriptor descriptor, Map<UUID, BigDecimal> balances,
                                                CurrencyAccount account) {
        if (account == null) {
            return null;
        }
        BigDecimal balance = balances.get(account.getPlayerId());
        return descriptor.normalize(balance != null ? balance : BigDecimal.ZERO);
    }

    private void writeAfter(Connection connection, CurrencyKey currency, CurrencyDescriptor descriptor, CurrencyAccount account,
                            BigDecimal after) throws SQLException {
        if (account != null) {
            ledger.writeBalance(connection, currency, account, descriptor.normalize(java.util.Objects.requireNonNull(after)));
        }
    }

    /**
     * Retries {@code block} on a serialization failure or deadlock (PostgreSQL, MySQL) and on a constraint violation:
     * a concurrent request may have inserted the same new account row or idempotency key, and the next attempt
     * sees that row instead of colliding with it.
     */
    private static <T> T retryOnConflict(SqlWork<T> block) throws SQLException {
        int attempt = 1;
        while (true) {
            try {
                return block.run();
            } catch (SQLException error) {
                if (!isConflict(error) || attempt >= MAX_ATTEMPTS) {
                    throw error;
                }
                attempt++;
            }
        }
    }

    /** True for serialization failure, deadlock and constraint violation SQL states. */
    private static boolean isConflict(SQLException error) {
        String state = error.getSQLState();
        return state != null && (state.equals("40001") || state.equals("40P01") || state.startsWith("23"));
    }

    @Override
    @NotNull
    public CompletionStage<CurrencyHistoryPage> history(@NotNull CurrencyKey currency, @NotNull CurrencyHistoryQuery query) {
        return async(() -> connection(connection -> historyQuery.page(connection, currency, query)));
    }

    @Override
    @NotNull
    public CompletionStage<CurrencyStorageSnapshot> export(@NotNull CurrencyKey currency) {
        return async(() -> connection(connection ->
                committed(connection, Connection.TRANSACTION_REPEATABLE_READ, () -> snapshots.export(connection, currency))));
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
            return connection(connection ->
                    committed(connection, Connection.TRANSACTION_SERIALIZABLE, () -> snapshots.importSnapshot(connection, snapshot, mode)));
        });
    }

    @Override
    public void close() {
        worker.close();
    }

    /** Creates the tables once per storage instance, on first use. */
    private void ensureSchema() throws SQLException {
        if (initialized) {
            return;
        }
        synchronized (schemaLock) {
            if (initialized) {
                return;
            }
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate(schema.createAccountsTableSql());
                statement.executeUpdate(schema.createTransactionsTableSql());
            }
            initialized = true;
        }
    }

    /** Borrows a pooled connection for {@code operation} and returns it afterwards. */
    private <T> T connection(ConnectionWork<T> operation) throws SQLException {
        ensureSchema();
        try (Connection connection = dataSource.getConnection()) {
            return operation.run(connection);
        }
    }

    /** Runs {@code operation} in one transaction with {@code isolation}; rolls back on failure and restores connection settings. */
    private static <T> T committed(Connection connection, int isolation, SqlWork<T> operation) throws SQLException {
        ConnectionSettings previous = ConnectionSettings.capture(connection);
        connection.setAutoCommit(false);
        connection.setTransactionIsolation(isolation);
        try {
            T result = operation.run();
            connection.commit();
            return result;
        } catch (Throwable error) {
            Failures.runCatchingNonFatal(() -> {
                connection.rollback();
                return null;
            }).onFailure(error::addSuppressed);
            throw error;
        } finally {
            previous.restore(connection);
        }
    }

    /** Runs {@code operation} on the storage worker pool so JDBC never blocks the caller. */
    private <T> CompletionStage<T> async(SqlWork<T> operation) {
        return worker.submit(operation::run);
    }
}
