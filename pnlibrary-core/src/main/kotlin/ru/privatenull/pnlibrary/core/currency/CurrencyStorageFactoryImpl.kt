package ru.privatenull.pnlibrary.core.currency

import ru.privatenull.pnlibrary.api.currency.CurrencyStorage
import ru.privatenull.pnlibrary.api.currency.CurrencyStorageFactory
import java.nio.file.Path
import javax.sql.DataSource

internal class CurrencyStorageFactoryImpl : CurrencyStorageFactory {
    override fun file(path: Path): CurrencyStorage = FileCurrencyStorage(path, 100_000)
    override fun file(path: Path, maximumTransactions: Int): CurrencyStorage = FileCurrencyStorage(path, maximumTransactions)
    override fun jdbc(dataSource: DataSource): CurrencyStorage = JdbcCurrencyStorage(dataSource, "pnlibrary_currency")
    override fun jdbc(dataSource: DataSource, tablePrefix: String): CurrencyStorage = JdbcCurrencyStorage(dataSource, tablePrefix)
}
