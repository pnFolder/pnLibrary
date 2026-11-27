package org.pnfolder.pnlibrary.currency.storage;

import org.pnfolder.pnlibrary.currency.CurrencyMutationEngine;
import org.pnfolder.pnlibrary.currency.CurrencyMutation;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount;
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor;
import org.pnfolder.pnlibrary.api.currency.CurrencyKey;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionRequest;

/** Balances, ledger and idempotency index of the file storage; changes made with an undo log can be taken back step by step. */
public final class CurrencyLedgerState {
    private final Map<String, BigDecimal> balances = new LinkedHashMap<>();
    private final ArrayDeque<CurrencyTransaction> transactions = new ArrayDeque<>();
    private final ArrayDeque<CurrencyTransaction> archivedReceipts = new ArrayDeque<>();
    private final Map<String, CurrencyTransaction> idempotency = new HashMap<>();

    /** Map key of one account balance: {@code currency|playerId}. */
    public static String accountKey(CurrencyKey currency, CurrencyAccount account) {
        return currency + "|" + account.getPlayerId();
    }

    /** Map key of one idempotency key, scoped per currency. */
    static String idempotencyKey(CurrencyKey currency, String key) {
        return currency + "|" + key;
    }

    public Map<String, BigDecimal> getBalances() {
        return balances;
    }

    public ArrayDeque<CurrencyTransaction> getTransactions() {
        return transactions;
    }

    ArrayDeque<CurrencyTransaction> archivedReceipts() {
        return archivedReceipts;
    }

    void recordArchivedReceipt(CurrencyTransaction transaction) {
        String key = transaction.getIdempotencyKey();
        if (key == null) {
            throw new IllegalArgumentException("An archived currency receipt requires an idempotency key");
        }
        archivedReceipts.addLast(transaction);
        idempotency.put(idempotencyKey(transaction.getCurrency(), key), transaction);
    }

    /** The earlier transaction recorded under the request's idempotency key, if any. */
    @Nullable
    public CurrencyTransaction replay(@NotNull CurrencyKey currency, @NotNull CurrencyTransactionRequest request) {
        String key = request.getIdempotencyKey();
        return key == null ? null : idempotency.get(idempotencyKey(currency, key));
    }

    public boolean hasIdempotencyKey(@NotNull CurrencyKey currency, @NotNull String key) {
        return idempotency.containsKey(idempotencyKey(currency, key));
    }

    /** Evaluates {@code request} against current balances, stores the new balances when it commits, records and trims the ledger. */
    @NotNull
    public CurrencyTransaction apply(@NotNull CurrencyKey currency, @NotNull CurrencyDescriptor descriptor,
                              @NotNull CurrencyTransactionRequest request, int maximumTransactions, @NotNull LedgerUndo undo) {
        String sourceKey = request.getSource() == null ? null : accountKey(currency, request.getSource());
        String targetKey = request.getTarget() == null ? null : accountKey(currency, request.getTarget());
        BigDecimal sourceBefore = sourceKey == null ? null : descriptor.normalize(balanceOf(sourceKey));
        BigDecimal targetBefore = targetKey == null ? null : descriptor.normalize(balanceOf(targetKey));
        CurrencyMutation mutation = CurrencyMutationEngine.evaluate(request, sourceBefore, targetBefore, descriptor);
        if (mutation.isCommitted()) {
            if (sourceKey != null) {
                setBalance(sourceKey, descriptor.normalize(java.util.Objects.requireNonNull(mutation.getSourceAfter())), undo);
            }
            if (targetKey != null) {
                setBalance(targetKey, descriptor.normalize(java.util.Objects.requireNonNull(mutation.getTargetAfter())), undo);
            }
        }
        CurrencyTransaction transaction = mutation.transaction(currency, descriptor, request);
        record(transaction, undo);
        trim(maximumTransactions, undo);
        return transaction;
    }

    private BigDecimal balanceOf(String key) {
        BigDecimal balance = balances.get(key);
        return balance != null ? balance : BigDecimal.ZERO;
    }

    /** Appends {@code transaction} to the ledger and indexes its idempotency key. */
    void record(@NotNull CurrencyTransaction transaction) {
        record(transaction, null);
    }

    /** Appends {@code transaction} to the ledger and indexes its idempotency key; {@code undo} can take it back. */
    void record(@NotNull CurrencyTransaction transaction, @Nullable LedgerUndo undo) {
        transactions.addLast(transaction);
        String key = transaction.getIdempotencyKey() == null ? null
                : idempotencyKey(transaction.getCurrency(), transaction.getIdempotencyKey());
        CurrencyTransaction previous = key == null ? null : idempotency.put(key, transaction);
        if (undo != null) {
            undo.add(() -> {
                transactions.removeLast();
                if (key != null) {
                    if (previous == null) {
                        idempotency.remove(key);
                    } else {
                        idempotency.put(key, previous);
                    }
                }
            });
        }
    }

    /** Drops the oldest history entries beyond {@code maximumTransactions}, retaining receipts for retries. */
    void trim(int maximumTransactions) {
        trim(maximumTransactions, null);
    }

    /** Like {@link #trim(int)}; {@code undo} can bring the dropped transactions back. */
    void trim(int maximumTransactions, @Nullable LedgerUndo undo) {
        while (transactions.size() > maximumTransactions) {
            CurrencyTransaction old = transactions.removeFirst();
            boolean keepReceipt = old.getIdempotencyKey() != null;
            if (keepReceipt) {
                archivedReceipts.addLast(old);
            }
            if (undo != null) {
                undo.add(() -> {
                    transactions.addFirst(old);
                    if (keepReceipt) {
                        archivedReceipts.removeLast();
                    }
                });
            }
        }
    }

    /** Deletes all balances, transactions and idempotency keys of {@code currency}. */
    public void removeCurrency(@NotNull CurrencyKey currency) {
        String prefix = currency + "|";
        balances.keySet().removeIf(key -> key.startsWith(prefix));
        transactions.removeIf(transaction -> transaction.getCurrency().equals(currency));
        archivedReceipts.removeIf(transaction -> transaction.getCurrency().equals(currency));
        idempotency.keySet().removeIf(key -> key.startsWith(prefix));
    }

    /** Full copy for operations that rewrite much of the state at once. */
    @NotNull
    public Copy copy() {
        return new Copy(new LinkedHashMap<>(balances), new ArrayList<>(transactions),
                new ArrayList<>(archivedReceipts), new HashMap<>(idempotency));
    }

    void restore(@NotNull Copy copy) {
        balances.clear();
        balances.putAll(copy.balances);
        transactions.clear();
        transactions.addAll(copy.transactions);
        archivedReceipts.clear();
        archivedReceipts.addAll(copy.archivedReceipts);
        idempotency.clear();
        idempotency.putAll(copy.idempotency);
    }

    private void setBalance(String key, BigDecimal value, LedgerUndo undo) {
        BigDecimal previous = balances.put(key, value);
        undo.add(() -> {
            if (previous == null) {
                balances.remove(key);
            } else {
                balances.put(key, previous);
            }
        });
    }

    /** A snapshot of the whole state, taken by {@link #copy()}. */
    static final class Copy {
        private final Map<String, BigDecimal> balances;
        private final List<CurrencyTransaction> transactions;
        private final List<CurrencyTransaction> archivedReceipts;
        private final Map<String, CurrencyTransaction> idempotency;

        Copy(Map<String, BigDecimal> balances, List<CurrencyTransaction> transactions,
             List<CurrencyTransaction> archivedReceipts, Map<String, CurrencyTransaction> idempotency) {
            this.balances = balances;
            this.transactions = transactions;
            this.archivedReceipts = archivedReceipts;
            this.idempotency = idempotency;
        }
    }
}
