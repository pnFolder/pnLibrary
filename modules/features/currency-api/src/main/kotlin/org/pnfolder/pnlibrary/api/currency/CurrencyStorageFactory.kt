package org.pnfolder.pnlibrary.api.currency

import java.nio.file.Path
import java.util.concurrent.CompletionStage
import javax.sql.DataSource

/** Creates supported persistent storage implementations without exposing core classes. */
interface CurrencyStorageFactory {
    /** Creates a JSON file storage retaining its default number of ledger rows. */
    fun file(path: Path): CurrencyStorage
    /** Creates a JSON file storage retaining at most [maximumTransactions] ledger rows. */
    fun file(path: Path, maximumTransactions: Int): CurrencyStorage
    /** Creates JDBC storage using the default table prefix. */
    fun jdbc(dataSource: DataSource): CurrencyStorage
    /** Creates JDBC storage using validated alphanumeric [tablePrefix]. */
    fun jdbc(dataSource: DataSource, tablePrefix: String): CurrencyStorage
    /** Consistently exports [sourceCurrency] and atomically imports it as [targetCurrency]. */
    fun migrate(
        source: CurrencyStorage,
        sourceCurrency: CurrencyKey,
        target: CurrencyStorage,
        targetCurrency: CurrencyKey,
        mode: CurrencyImportMode,
    ): CompletionStage<CurrencyMigrationResult>
}
