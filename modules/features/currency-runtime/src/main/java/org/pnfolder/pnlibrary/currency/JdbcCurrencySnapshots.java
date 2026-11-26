package org.pnfolder.pnlibrary.currency;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount;
import org.pnfolder.pnlibrary.api.currency.CurrencyImportMode;
import org.pnfolder.pnlibrary.api.currency.CurrencyImportResult;
import org.pnfolder.pnlibrary.api.currency.CurrencyKey;
import org.pnfolder.pnlibrary.api.currency.CurrencyStorageSnapshot;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction;

/** Reads and writes whole-currency snapshots inside a caller-managed JDBC transaction. */
final class JdbcCurrencySnapshots {
    private final JdbcCurrencyLedger ledger;
    private final JdbcTransactionDecoder decoder;
    private final String accountsTable;
    private final String transactionsTable;

    JdbcCurrencySnapshots(@NotNull JdbcCurrencySchema schema, @NotNull JdbcCurrencyLedger ledger,
                          @NotNull JdbcTransactionDecoder decoder) {
        this.ledger = ledger;
        this.decoder = decoder;
        this.accountsTable = schema.getAccountsTable();
        this.transactionsTable = schema.getTransactionsTable();
    }

    /** Reads all balances and the full ledger of {@code currency} in the caller's transaction. */
    @NotNull
    CurrencyStorageSnapshot export(@NotNull Connection connection, @NotNull CurrencyKey currency) throws SQLException {
        Map<UUID, BigDecimal> balances = new LinkedHashMap<UUID, BigDecimal>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT account_id, balance FROM " + accountsTable + " WHERE currency_id = ?")) {
            statement.setString(1, currency.toString());
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    balances.put(UUID.fromString(results.getString(1)), results.getBigDecimal(2));
                }
            }
        }
        List<CurrencyTransaction> transactions = new ArrayList<CurrencyTransaction>();
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM " + transactionsTable + " WHERE currency_id = ? ORDER BY created_at ASC")) {
            statement.setString(1, currency.toString());
            try (ResultSet results = statement.executeQuery()) {
                while (results.next()) {
                    transactions.add(decoder.decode(results));
                }
            }
        }
        return new CurrencyStorageSnapshot(currency, Instant.now(), balances, transactions);
    }

    /** Writes {@code snapshot} according to {@code mode} and counts imported and skipped rows. */
    @NotNull
    CurrencyImportResult importSnapshot(@NotNull Connection connection, @NotNull CurrencyStorageSnapshot snapshot,
                                        @NotNull CurrencyImportMode mode) throws SQLException {
        if (mode == CurrencyImportMode.REPLACE) {
            ledger.deleteCurrency(connection, snapshot.getCurrency());
        }
        int importedAccounts = 0;
        int skippedAccounts = 0;
        for (Map.Entry<UUID, BigDecimal> entry : snapshot.getBalances().entrySet()) {
            CurrencyAccount account = new CurrencyAccount(entry.getKey());
            boolean exists = ledger.accountExists(connection, snapshot.getCurrency(), account);
            if (exists && mode == CurrencyImportMode.MERGE_KEEP_TARGET) {
                skippedAccounts++;
            } else {
                ledger.writeBalance(connection, snapshot.getCurrency(), account, entry.getValue());
                importedAccounts++;
            }
        }
        int importedTransactions = 0;
        int skippedTransactions = 0;
        for (CurrencyTransaction transaction : snapshot.getTransactions()) {
            if (ledger.transactionExists(connection, transaction)) {
                skippedTransactions++;
            } else {
                ledger.insertTransaction(connection, transaction);
                importedTransactions++;
            }
        }
        return new CurrencyImportResult(snapshot.getCurrency(), mode, importedAccounts, skippedAccounts,
                importedTransactions, skippedTransactions);
    }
}
