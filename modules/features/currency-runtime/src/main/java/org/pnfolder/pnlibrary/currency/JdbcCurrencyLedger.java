package org.pnfolder.pnlibrary.currency;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount;
import org.pnfolder.pnlibrary.api.currency.CurrencyKey;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction;

/** Row-level balance and ledger statements of {@link JdbcCurrencySchema}, run on a caller-owned connection. */
final class JdbcCurrencyLedger {
    private final JdbcCurrencySchema schema;
    private final Gson gson;
    private final JdbcTransactionDecoder decoder;
    private final String accountsTable;
    private final String transactionsTable;

    JdbcCurrencyLedger(@NotNull JdbcCurrencySchema schema, @NotNull Gson gson, @NotNull JdbcTransactionDecoder decoder) {
        this.schema = schema;
        this.gson = gson;
        this.decoder = decoder;
        this.accountsTable = schema.getAccountsTable();
        this.transactionsTable = schema.getTransactionsTable();
    }

    /** Returns the balance without locking; absent accounts read as zero. */
    @NotNull
    BigDecimal readBalance(@NotNull Connection connection, @NotNull CurrencyKey currency, @NotNull CurrencyAccount account)
            throws SQLException {
        return selectBalance(connection, currency, account, "");
    }

    /** Reads the balance and locks its row until the transaction ends; SQLite has no row locks and needs none. */
    @NotNull
    BigDecimal lockBalance(@NotNull Connection connection, @NotNull CurrencyKey currency, @NotNull CurrencyAccount account)
            throws SQLException {
        boolean sqlite = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT).contains("sqlite");
        return selectBalance(connection, currency, account, sqlite ? "" : " FOR UPDATE");
    }

    /** Selects the balance row, appending {@code suffix} (a lock clause or nothing). */
    private BigDecimal selectBalance(Connection connection, CurrencyKey currency, CurrencyAccount account, String suffix)
            throws SQLException {
        String sql = "SELECT balance FROM " + accountsTable + " WHERE currency_id = ? AND account_id = ?" + suffix;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, currency.toString());
            statement.setString(2, account.getPlayerId().toString());
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? results.getBigDecimal(1) : BigDecimal.ZERO;
            }
        }
    }

    /**
     * Updates the balance row of {@code account} or inserts it. A concurrent insert of the same row fails with a
     * constraint violation that {@link JdbcCurrencyStorage} retries; recovering here is impossible on PostgreSQL,
     * where any failed statement aborts the whole transaction.
     */
    void writeBalance(@NotNull Connection connection, @NotNull CurrencyKey currency, @NotNull CurrencyAccount account,
                      @NotNull BigDecimal balance) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE " + accountsTable + " SET balance = ? WHERE currency_id = ? AND account_id = ?")) {
            statement.setBigDecimal(1, balance);
            statement.setString(2, currency.toString());
            statement.setString(3, account.getPlayerId().toString());
            if (statement.executeUpdate() > 0) {
                return;
            }
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + accountsTable + " (currency_id, account_id, balance) VALUES (?, ?, ?)")) {
            statement.setString(1, currency.toString());
            statement.setString(2, account.getPlayerId().toString());
            statement.setBigDecimal(3, balance);
            statement.executeUpdate();
        }
    }

    /** Returns whether a balance row exists for {@code account}. */
    boolean accountExists(@NotNull Connection connection, @NotNull CurrencyKey currency, @NotNull CurrencyAccount account)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM " + accountsTable + " WHERE currency_id = ? AND account_id = ?")) {
            statement.setString(1, currency.toString());
            statement.setString(2, account.getPlayerId().toString());
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        }
    }

    /** Returns whether {@code transaction} is already recorded by ID or idempotency key. */
    boolean transactionExists(@NotNull Connection connection, @NotNull CurrencyTransaction transaction) throws SQLException {
        String key = transaction.getIdempotencyKey();
        String sql = key == null
                ? "SELECT 1 FROM " + transactionsTable + " WHERE transaction_id = ?"
                : "SELECT 1 FROM " + transactionsTable + " WHERE transaction_id = ? OR (currency_id = ? AND idempotency_key = ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, transaction.getId().toString());
            if (key != null) {
                statement.setString(2, transaction.getCurrency().toString());
                statement.setString(3, key);
            }
            try (ResultSet results = statement.executeQuery()) {
                return results.next();
            }
        }
    }

    /** Inserts one ledger row. */
    void insertTransaction(@NotNull Connection connection, @NotNull CurrencyTransaction value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(schema.insertTransactionSql())) {
            List<Object> values = Arrays.<Object>asList(
                    value.getId().toString(),
                    value.getCurrency().toString(),
                    value.getType().name(),
                    value.getStatus().name(),
                    value.getAmount(),
                    value.getSource() == null ? null : value.getSource().getPlayerId().toString(),
                    value.getTarget() == null ? null : value.getTarget().getPlayerId().toString(),
                    value.getActor().getType().name(),
                    value.getActor().getId(),
                    value.getService(),
                    value.getReason(),
                    gson.toJson(value.getMetadata()),
                    value.getSourceBalanceBefore(),
                    value.getSourceBalanceAfter(),
                    value.getTargetBalanceBefore(),
                    value.getTargetBalanceAfter(),
                    Timestamp.from(value.getCreatedAt()),
                    value.getFailure(),
                    value.getIdempotencyKey());
            for (int index = 0; index < values.size(); index++) {
                statement.setObject(index + 1, values.get(index));
            }
            statement.executeUpdate();
        }
    }

    /** Returns the transaction already stored under {@code key}, or {@code null} when the key is new. */
    @Nullable
    CurrencyTransaction findIdempotent(@NotNull Connection connection, @NotNull CurrencyKey currency, @NotNull String key)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM " + transactionsTable + " WHERE currency_id = ? AND idempotency_key = ?")) {
            statement.setString(1, currency.toString());
            statement.setString(2, key);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? decoder.decode(results) : null;
            }
        }
    }

    /** Deletes all balances and ledger rows of {@code currency}. */
    void deleteCurrency(@NotNull Connection connection, @NotNull CurrencyKey currency) throws SQLException {
        for (String table : Arrays.asList(transactionsTable, accountsTable)) {
            try (PreparedStatement statement = connection.prepareStatement("DELETE FROM " + table + " WHERE currency_id = ?")) {
                statement.setString(1, currency.toString());
                statement.executeUpdate();
            }
        }
    }
}
