package org.pnfolder.pnlibrary.currency.storage;

import java.util.Objects;
import org.pnfolder.pnlibrary.currency.storage.CurrencyLedgerState;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount;
import org.pnfolder.pnlibrary.api.currency.CurrencyImportMode;
import org.pnfolder.pnlibrary.api.currency.CurrencyImportResult;
import org.pnfolder.pnlibrary.api.currency.CurrencyStorageSnapshot;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction;

/** Moves the balances and transactions of a snapshot into the ledger state of the file storage. */
final class FileLedgerImport {
    private final CurrencyLedgerState state;
    private final int maximumTransactions;

    public FileLedgerImport(@NotNull CurrencyLedgerState state, int maximumTransactions) {
        this.state = Objects.requireNonNull(state, "state");
        this.maximumTransactions = maximumTransactions;
    }

    /** Applies {@code snapshot} to the state according to {@code mode}; the caller saves or restores. */
    @NotNull
    CurrencyImportResult apply(@NotNull CurrencyStorageSnapshot snapshot, @NotNull CurrencyImportMode mode) {
        if (mode == CurrencyImportMode.REPLACE) {
            state.removeCurrency(snapshot.getCurrency());
        }
        int[] accounts = importBalances(snapshot, mode);
        int[] moved = importTransactions(snapshot);
        state.trim(maximumTransactions);
        return new CurrencyImportResult(snapshot.getCurrency(), mode, accounts[0], accounts[1], moved[0], moved[1]);
    }

    /** Returns how many accounts were imported and how many were skipped because the target already had them. */
    private int[] importBalances(CurrencyStorageSnapshot snapshot, CurrencyImportMode mode) {
        int imported = 0;
        int skipped = 0;
        for (Map.Entry<UUID, BigDecimal> entry : snapshot.getBalances().entrySet()) {
            String key = CurrencyLedgerState.accountKey(snapshot.getCurrency(), new CurrencyAccount(entry.getKey()));
            if (mode == CurrencyImportMode.MERGE_KEEP_TARGET && state.getBalances().containsKey(key)) {
                skipped++;
            } else {
                state.getBalances().put(key, entry.getValue());
                imported++;
            }
        }
        return new int[] {imported, skipped};
    }

    /** Returns how many transactions were imported and how many were skipped as duplicates. */
    private int[] importTransactions(CurrencyStorageSnapshot snapshot) {
        int imported = 0;
        int skipped = 0;
        Set<UUID> ids = new HashSet<>();
        for (CurrencyTransaction transaction : state.getTransactions()) {
            ids.add(transaction.getId());
        }
        for (CurrencyTransaction transaction : snapshot.getTransactions()) {
            boolean duplicate = transaction.getIdempotencyKey() != null
                    && state.hasIdempotencyKey(snapshot.getCurrency(), transaction.getIdempotencyKey());
            if (ids.contains(transaction.getId()) || duplicate) {
                skipped++;
            } else {
                state.record(transaction);
                ids.add(transaction.getId());
                imported++;
            }
        }
        return new int[] {imported, skipped};
    }
}
