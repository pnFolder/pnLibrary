package org.pnfolder.pnlibrary.currency;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionStage;
import javax.sql.DataSource;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.currency.CurrencyImportMode;
import org.pnfolder.pnlibrary.api.currency.CurrencyKey;
import org.pnfolder.pnlibrary.api.currency.CurrencyMigrationResult;
import org.pnfolder.pnlibrary.api.currency.CurrencyStorage;
import org.pnfolder.pnlibrary.api.currency.CurrencyStorageFactory;
import org.pnfolder.pnlibrary.api.currency.CurrencyStorageSnapshot;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction;

/** Creates built-in storage backends (file keeps 100 000 transactions by default) and migrates currencies between them. */
final class BuiltInCurrencyStorage implements CurrencyStorageFactory {
    @Override
    @NotNull
    public CurrencyStorage file(@NotNull Path path) {
        return new FileCurrencyStorage(path, 100_000);
    }

    @Override
    @NotNull
    public CurrencyStorage file(@NotNull Path path, int maximumTransactions) {
        return new FileCurrencyStorage(path, maximumTransactions);
    }

    @Override
    @NotNull
    public CurrencyStorage jdbc(@NotNull DataSource dataSource) {
        return new JdbcCurrencyStorage(dataSource, "pnlibrary_currency");
    }

    @Override
    @NotNull
    public CurrencyStorage jdbc(@NotNull DataSource dataSource, @NotNull String tablePrefix) {
        return new JdbcCurrencyStorage(dataSource, tablePrefix);
    }

    @Override
    @NotNull
    public CompletionStage<CurrencyMigrationResult> migrate(@NotNull CurrencyStorage source, @NotNull CurrencyKey sourceCurrency,
                                                            @NotNull CurrencyStorage target, @NotNull CurrencyKey targetCurrency,
                                                            @NotNull CurrencyImportMode mode) {
        if (source == target && sourceCurrency.equals(targetCurrency)) {
            throw new IllegalArgumentException("Migration source and target are identical");
        }
        Instant startedAt = Instant.now();
        return source.export(sourceCurrency).thenCompose(exported -> {
            List<CurrencyTransaction> remapped = new ArrayList<CurrencyTransaction>();
            for (CurrencyTransaction transaction : exported.getTransactions()) {
                remapped.add(transaction.toBuilder().currency(targetCurrency).build());
            }
            CurrencyStorageSnapshot snapshot = new CurrencyStorageSnapshot(exported.getFormatVersion(), targetCurrency,
                    exported.getCreatedAt(), exported.getBalances(), remapped);
            return target.importSnapshot(snapshot, mode).thenApply(result ->
                    new CurrencyMigrationResult(sourceCurrency, targetCurrency, result, startedAt, Instant.now()));
        });
    }
}
