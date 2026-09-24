package ru.privatenull.pnlibrary.currency

import ru.privatenull.pnlibrary.api.currency.CurrencyImportMode
import ru.privatenull.pnlibrary.api.currency.CurrencyKey
import ru.privatenull.pnlibrary.api.currency.CurrencyMigrationResult
import ru.privatenull.pnlibrary.api.currency.CurrencyStorage
import ru.privatenull.pnlibrary.api.currency.CurrencyStorageFactory
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CompletionStage
import javax.sql.DataSource

/** Creates built-in storage backends and coordinates portable snapshot migrations. */
internal class CurrencyStorageFactoryImpl : CurrencyStorageFactory {
    override fun file(path: Path): CurrencyStorage = FileCurrencyStorage(path, 100_000)
    override fun file(path: Path, maximumTransactions: Int): CurrencyStorage = FileCurrencyStorage(path, maximumTransactions)
    override fun jdbc(dataSource: DataSource): CurrencyStorage = JdbcCurrencyStorage(dataSource, "pnlibrary_currency")
    override fun jdbc(dataSource: DataSource, tablePrefix: String): CurrencyStorage = JdbcCurrencyStorage(dataSource, tablePrefix)
    override fun migrate(
        source: CurrencyStorage,
        sourceCurrency: CurrencyKey,
        target: CurrencyStorage,
        targetCurrency: CurrencyKey,
        mode: CurrencyImportMode,
    ): CompletionStage<CurrencyMigrationResult> {
        require(source !== target || sourceCurrency != targetCurrency) { "Migration source and target are identical" }
        val startedAt = Instant.now()

        return source.export(sourceCurrency).thenCompose { exported ->
            val remapped = exported.copy(
                currency = targetCurrency,
                transactions = exported.transactions.map { it.copy(currency = targetCurrency) },
            )

            target.importSnapshot(remapped, mode).thenApply { result ->
                CurrencyMigrationResult(
                    sourceCurrency = sourceCurrency,
                    targetCurrency = targetCurrency,
                    import = result,
                    startedAt = startedAt,
                    completedAt = Instant.now(),
                )
            }
        }
    }
}
