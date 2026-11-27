package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.concurrent.CompletionStage;
import javax.sql.DataSource;

/** Creates supported persistent storage implementations without exposing core classes. */
public interface CurrencyStorageFactory {
    /** Creates a JSON file storage with the default history limit. Receipts for idempotent retries outlive the history. */
    @NotNull
    CurrencyStorage file(@NotNull Path path);

    /** Creates a JSON file storage showing at most {@code maximumTransactions} rows in its history. */
    @NotNull
    CurrencyStorage file(@NotNull Path path, int maximumTransactions);

    /** Creates JDBC storage using the default table prefix. */
    @NotNull
    CurrencyStorage jdbc(@NotNull DataSource dataSource);

    /** Creates JDBC storage using validated alphanumeric {@code tablePrefix}. */
    @NotNull
    CurrencyStorage jdbc(@NotNull DataSource dataSource, @NotNull String tablePrefix);

    /** Consistently exports {@code sourceCurrency} and atomically imports it as {@code targetCurrency}. */
    @NotNull
    CompletionStage<CurrencyMigrationResult> migrate(
            @NotNull CurrencyStorage source, @NotNull CurrencyKey sourceCurrency,
            @NotNull CurrencyStorage target, @NotNull CurrencyKey targetCurrency,
            @NotNull CurrencyImportMode mode);
}
