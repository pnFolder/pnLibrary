package org.pnfolder.pnlibrary.currency;

import java.util.concurrent.CompletableFuture;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor;
import org.pnfolder.pnlibrary.api.currency.CurrencyKey;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionRequest;

/** A balance change that waits for the next batch. */
final class TransactJob implements StorageJob {
    private final CurrencyKey currency;
    private final CurrencyDescriptor descriptor;
    private final CurrencyTransactionRequest request;
    private final CompletableFuture<CurrencyTransaction> result = new CompletableFuture<CurrencyTransaction>();

    TransactJob(@NotNull CurrencyKey currency, @NotNull CurrencyDescriptor descriptor, @NotNull CurrencyTransactionRequest request) {
        this.currency = currency;
        this.descriptor = descriptor;
        this.request = request;
    }

    CurrencyKey getCurrency() {
        return currency;
    }

    CurrencyDescriptor getDescriptor() {
        return descriptor;
    }

    CurrencyTransactionRequest getRequest() {
        return request;
    }

    CompletableFuture<CurrencyTransaction> getResult() {
        return result;
    }

    void finish(@NotNull CurrencyTransaction transaction) {
        result.complete(transaction);
    }

    @Override
    public void fail(@NotNull Throwable error) {
        result.completeExceptionally(error);
    }
}
