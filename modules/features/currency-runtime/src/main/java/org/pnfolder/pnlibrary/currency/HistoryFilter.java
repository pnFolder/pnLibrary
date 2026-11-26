package org.pnfolder.pnlibrary.currency;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.currency.CurrencyHistoryQuery;
import org.pnfolder.pnlibrary.api.currency.CurrencyKey;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction;

/** The filters of a {@link CurrencyHistoryQuery} applied to transactions held in memory. */
final class HistoryFilter {
    private HistoryFilter() {
    }

    /** Applies every non-null filter of {@code query} with logical AND. */
    static boolean matches(@NotNull CurrencyTransaction transaction, @NotNull CurrencyKey currency, @NotNull CurrencyHistoryQuery query) {
        return transaction.getCurrency().equals(currency)
                && (query.getAccount() == null || (transaction.getSource() != null && transaction.getSource().getPlayerId().equals(query.getAccount()))
                        || (transaction.getTarget() != null && transaction.getTarget().getPlayerId().equals(query.getAccount())))
                && (query.getActor() == null || transaction.getActor().equals(query.getActor()))
                && (query.getService() == null || transaction.getService().equalsIgnoreCase(query.getService()))
                && (query.getTypes().isEmpty() || query.getTypes().contains(transaction.getType()))
                && (query.getFrom() == null || !transaction.getCreatedAt().isBefore(query.getFrom()))
                && (query.getUntil() == null || transaction.getCreatedAt().isBefore(query.getUntil()));
    }
}
