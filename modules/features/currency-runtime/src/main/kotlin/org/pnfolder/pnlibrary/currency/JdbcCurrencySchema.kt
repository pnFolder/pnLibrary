package org.pnfolder.pnlibrary.currency

/** Table names and DDL/DML text for [JdbcCurrencyStorage]. */
internal class JdbcCurrencySchema(tablePrefix: String) {
    init {
        require(tablePrefix.matches(PREFIX_PATTERN)) { "Invalid currency table prefix" }
    }

    /** Table holding one balance row per currency and account. */
    val accountsTable = "${tablePrefix}_accounts"
    /** Table holding the append-only ledger. */
    val transactionsTable = "${tablePrefix}_transactions"

    /** DDL of the balances table. */
    fun createAccountsTableSql(): String = """
        CREATE TABLE IF NOT EXISTS $accountsTable (
            currency_id VARCHAR(190) NOT NULL,
            account_id VARCHAR(36) NOT NULL,
            balance DECIMAL(38,18) NOT NULL,
            PRIMARY KEY (currency_id, account_id)
        )
    """.trimIndent()

    /** DDL of the ledger table; the unique key makes idempotent retries safe. */
    fun createTransactionsTableSql(): String = """
        CREATE TABLE IF NOT EXISTS $transactionsTable (
            transaction_id VARCHAR(36) PRIMARY KEY,
            currency_id VARCHAR(190) NOT NULL,
            transaction_type VARCHAR(32) NOT NULL,
            status VARCHAR(16) NOT NULL,
            amount DECIMAL(38,18) NOT NULL,
            source_account VARCHAR(36),
            target_account VARCHAR(36),
            actor_type VARCHAR(16) NOT NULL,
            actor_id VARCHAR(190) NOT NULL,
            service_id VARCHAR(190) NOT NULL,
            reason_text TEXT,
            metadata_text TEXT NOT NULL,
            source_before DECIMAL(38,18),
            source_after DECIMAL(38,18),
            target_before DECIMAL(38,18),
            target_after DECIMAL(38,18),
            created_at TIMESTAMP NOT NULL,
            failure_text TEXT,
            idempotency_key VARCHAR(190),
            UNIQUE (currency_id, idempotency_key)
        )
    """.trimIndent()

    /** Insert statement whose 19 placeholders follow the column order of the ledger table. */
    fun insertTransactionSql(): String = """
        INSERT INTO $transactionsTable (
            transaction_id, currency_id, transaction_type, status, amount,
            source_account, target_account, actor_type, actor_id, service_id,
            reason_text, metadata_text, source_before, source_after,
            target_before, target_after, created_at, failure_text, idempotency_key
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """.trimIndent()

    private companion object {
        val PREFIX_PATTERN = Regex("[A-Za-z0-9_]+")
    }
}
