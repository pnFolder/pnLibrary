package org.pnfolder.pnlibrary.currency.storage;

import java.util.regex.Pattern;

import org.jetbrains.annotations.NotNull;

/** Table names and DDL/DML text for {@link JdbcCurrencyStorage}. */
final class JdbcCurrencySchema {
    private static final Pattern PREFIX_PATTERN = Pattern.compile("[A-Za-z0-9_]+");

    private final String accountsTable;
    private final String transactionsTable;

    public JdbcCurrencySchema(@NotNull String tablePrefix) {
        if (!PREFIX_PATTERN.matcher(tablePrefix).matches()) {
            throw new IllegalArgumentException("Invalid currency table prefix");
        }
        this.accountsTable = tablePrefix + "_accounts";
        this.transactionsTable = tablePrefix + "_transactions";
    }

    /** Table holding one balance row per currency and account. */
    @NotNull
    String getAccountsTable() {
        return accountsTable;
    }

    /** Table holding the append-only ledger. */
    @NotNull
    String getTransactionsTable() {
        return transactionsTable;
    }

    /** DDL of the balances table. */
    @NotNull
    String createAccountsTableSql() {
        return "CREATE TABLE IF NOT EXISTS " + accountsTable + " (\n"
                + "    currency_id VARCHAR(190) NOT NULL,\n"
                + "    account_id VARCHAR(36) NOT NULL,\n"
                + "    balance DECIMAL(38,18) NOT NULL,\n"
                + "    PRIMARY KEY (currency_id, account_id)\n"
                + ")";
    }

    /** DDL of the ledger table; the unique key makes idempotent retries safe. */
    @NotNull
    String createTransactionsTableSql() {
        return "CREATE TABLE IF NOT EXISTS " + transactionsTable + " (\n"
                + "    transaction_id VARCHAR(36) PRIMARY KEY,\n"
                + "    currency_id VARCHAR(190) NOT NULL,\n"
                + "    transaction_type VARCHAR(32) NOT NULL,\n"
                + "    status VARCHAR(16) NOT NULL,\n"
                + "    amount DECIMAL(38,18) NOT NULL,\n"
                + "    source_account VARCHAR(36),\n"
                + "    target_account VARCHAR(36),\n"
                + "    actor_type VARCHAR(16) NOT NULL,\n"
                + "    actor_id VARCHAR(190) NOT NULL,\n"
                + "    service_id VARCHAR(190) NOT NULL,\n"
                + "    reason_text TEXT,\n"
                + "    metadata_text TEXT NOT NULL,\n"
                + "    source_before DECIMAL(38,18),\n"
                + "    source_after DECIMAL(38,18),\n"
                + "    target_before DECIMAL(38,18),\n"
                + "    target_after DECIMAL(38,18),\n"
                + "    created_at TIMESTAMP NOT NULL,\n"
                + "    failure_text TEXT,\n"
                + "    idempotency_key VARCHAR(190),\n"
                + "    UNIQUE (currency_id, idempotency_key)\n"
                + ")";
    }

    /** Insert statement whose 19 placeholders follow the column order of the ledger table. */
    @NotNull
    String insertTransactionSql() {
        return "INSERT INTO " + transactionsTable + " (\n"
                + "    transaction_id, currency_id, transaction_type, status, amount,\n"
                + "    source_account, target_account, actor_type, actor_id, service_id,\n"
                + "    reason_text, metadata_text, source_before, source_after,\n"
                + "    target_before, target_after, created_at, failure_text, idempotency_key\n"
                + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
    }
}
