package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/** Row-level summary of a completed snapshot import. */
public final class CurrencyImportResult {
    private final CurrencyKey currency;
    private final CurrencyImportMode mode;
    private final int importedAccounts;
    private final int skippedAccounts;
    private final int importedTransactions;
    private final int skippedTransactions;

    /**
     * Creates a summary.
     *
     * @param currency imported currency identity
     * @param mode conflict policy applied to existing target data
     * @param importedAccounts balance rows inserted or overwritten
     * @param skippedAccounts existing balance rows retained
     * @param importedTransactions ledger rows inserted after duplicate checks
     * @param skippedTransactions ledger rows already identified by transaction ID or idempotency key
     */
    public CurrencyImportResult(@NotNull CurrencyKey currency, @NotNull CurrencyImportMode mode, int importedAccounts,
                                int skippedAccounts, int importedTransactions, int skippedTransactions) {
        this.currency = Objects.requireNonNull(currency, "currency");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.importedAccounts = importedAccounts;
        this.skippedAccounts = skippedAccounts;
        this.importedTransactions = importedTransactions;
        this.skippedTransactions = skippedTransactions;
    }

    /** Imported currency. */
    @NotNull
    public CurrencyKey getCurrency() {
        return currency;
    }

    /** Conflict policy used for the import. */
    @NotNull
    public CurrencyImportMode getMode() {
        return mode;
    }

    /** Balance rows inserted or overwritten. */
    public int getImportedAccounts() {
        return importedAccounts;
    }

    /** Existing balance rows retained. */
    public int getSkippedAccounts() {
        return skippedAccounts;
    }

    /** Ledger rows inserted after duplicate checks. */
    public int getImportedTransactions() {
        return importedTransactions;
    }

    /** Rows skipped because transaction ID or idempotency key already existed. */
    public int getSkippedTransactions() {
        return skippedTransactions;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CurrencyImportResult)) {
            return false;
        }
        CurrencyImportResult that = (CurrencyImportResult) other;
        return importedAccounts == that.importedAccounts && skippedAccounts == that.skippedAccounts
                && importedTransactions == that.importedTransactions
                && skippedTransactions == that.skippedTransactions
                && currency.equals(that.currency) && mode == that.mode;
    }

    @Override
    public int hashCode() {
        return Objects.hash(currency, mode, importedAccounts, skippedAccounts, importedTransactions, skippedTransactions);
    }

    @Override
    public String toString() {
        return "CurrencyImportResult(currency=" + currency + ", mode=" + mode + ", importedAccounts="
                + importedAccounts + ", skippedAccounts=" + skippedAccounts + ", importedTransactions="
                + importedTransactions + ", skippedTransactions=" + skippedTransactions + ")";
    }
}
