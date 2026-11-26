package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Portable, consistent export of one currency's balances and ledger. */
public final class CurrencyStorageSnapshot {
    private static final int FORMAT_VERSION = 1;

    private final int formatVersion;
    private final CurrencyKey currency;
    private final Instant createdAt;
    private final Map<UUID, BigDecimal> balances;
    private final List<CurrencyTransaction> transactions;

    /** Creates a snapshot of the current format version. */
    public CurrencyStorageSnapshot(@NotNull CurrencyKey currency, @NotNull Instant createdAt,
                                   @NotNull Map<UUID, BigDecimal> balances,
                                   @NotNull List<CurrencyTransaction> transactions) {
        this(FORMAT_VERSION, currency, createdAt, balances, transactions);
    }

    /**
     * Creates a snapshot.
     *
     * @param formatVersion serialization contract version; currently exactly {@code 1}
     * @param currency currency represented by every contained record
     * @param createdAt export creation instant
     * @param balances stored balances keyed by player UUID
     * @param transactions ledger records in storage export order
     */
    public CurrencyStorageSnapshot(int formatVersion, @NotNull CurrencyKey currency, @NotNull Instant createdAt,
                                   @NotNull Map<UUID, BigDecimal> balances,
                                   @NotNull List<CurrencyTransaction> transactions) {
        if (formatVersion != FORMAT_VERSION) {
            throw new IllegalArgumentException("Unsupported currency snapshot version: " + formatVersion);
        }
        this.formatVersion = formatVersion;
        this.currency = currency;
        this.createdAt = createdAt;
        this.balances = balances;
        this.transactions = transactions;
    }

    /** Serialization contract version. */
    public int getFormatVersion() {
        return formatVersion;
    }

    /** Currency represented by every contained record. */
    @NotNull
    public CurrencyKey getCurrency() {
        return currency;
    }

    /** Export creation instant. */
    @NotNull
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Stored balances keyed by player UUID. */
    @NotNull
    public Map<UUID, BigDecimal> getBalances() {
        return balances;
    }

    /** Ledger records in storage export order. */
    @NotNull
    public List<CurrencyTransaction> getTransactions() {
        return transactions;
    }

    /** Returns a copy of this snapshot with other balances and transactions. */
    @NotNull
    public CurrencyStorageSnapshot with(@NotNull Map<UUID, BigDecimal> balances,
                                        @NotNull List<CurrencyTransaction> transactions) {
        return new CurrencyStorageSnapshot(formatVersion, currency, createdAt, balances, transactions);
    }

    /** Returns a copy of this snapshot that describes another currency. */
    @NotNull
    public CurrencyStorageSnapshot withCurrency(@NotNull CurrencyKey currency) {
        return new CurrencyStorageSnapshot(formatVersion, currency, createdAt, balances, transactions);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CurrencyStorageSnapshot)) {
            return false;
        }
        CurrencyStorageSnapshot that = (CurrencyStorageSnapshot) other;
        return formatVersion == that.formatVersion && currency.equals(that.currency)
                && createdAt.equals(that.createdAt) && balances.equals(that.balances)
                && transactions.equals(that.transactions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(formatVersion, currency, createdAt, balances, transactions);
    }

    @Override
    public String toString() {
        return "CurrencyStorageSnapshot(formatVersion=" + formatVersion + ", currency=" + currency
                + ", createdAt=" + createdAt + ", balances=" + balances + ", transactions=" + transactions + ")";
    }
}
