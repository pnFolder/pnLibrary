package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Objects;

/** Timing and import result produced by a cross-storage currency migration. */
public final class CurrencyMigrationResult {
    private final CurrencyKey sourceCurrency;
    private final CurrencyKey targetCurrency;
    private final CurrencyImportResult importResult;
    private final Instant startedAt;
    private final Instant completedAt;

    /** Creates a migration result. */
    public CurrencyMigrationResult(@NotNull CurrencyKey sourceCurrency, @NotNull CurrencyKey targetCurrency,
                                   @NotNull CurrencyImportResult importResult, @NotNull Instant startedAt,
                                   @NotNull Instant completedAt) {
        this.sourceCurrency = sourceCurrency;
        this.targetCurrency = targetCurrency;
        this.importResult = importResult;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    /** Currency read from the source storage. */
    @NotNull
    public CurrencyKey getSourceCurrency() {
        return sourceCurrency;
    }

    /** Currency written in the target storage. */
    @NotNull
    public CurrencyKey getTargetCurrency() {
        return targetCurrency;
    }

    /** Detailed target import counters. */
    @NotNull
    public CurrencyImportResult getImport() {
        return importResult;
    }

    /** Instant before export began. */
    @NotNull
    public Instant getStartedAt() {
        return startedAt;
    }

    /** Instant after target import completed. */
    @NotNull
    public Instant getCompletedAt() {
        return completedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CurrencyMigrationResult)) {
            return false;
        }
        CurrencyMigrationResult that = (CurrencyMigrationResult) other;
        return sourceCurrency.equals(that.sourceCurrency) && targetCurrency.equals(that.targetCurrency)
                && importResult.equals(that.importResult) && startedAt.equals(that.startedAt)
                && completedAt.equals(that.completedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceCurrency, targetCurrency, importResult, startedAt, completedAt);
    }

    @Override
    public String toString() {
        return "CurrencyMigrationResult(sourceCurrency=" + sourceCurrency + ", targetCurrency=" + targetCurrency
                + ", import=" + importResult + ", startedAt=" + startedAt + ", completedAt=" + completedAt + ")";
    }
}
