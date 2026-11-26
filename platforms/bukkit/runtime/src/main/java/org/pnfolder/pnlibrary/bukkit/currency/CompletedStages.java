package org.pnfolder.pnlibrary.bukkit.currency;

import java.math.BigDecimal;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Turns a reflective call into a finished stage, and reads the numbers providers return. */
final class CompletedStages {
    private CompletedStages() {
    }

    static <T> CompletionStage<T> completed(Callable<T> operation) {
        try {
            return CompletableFuture.completedFuture(operation.call());
        } catch (Throwable error) {
            CompletableFuture<T> failed = new CompletableFuture<>();
            failed.completeExceptionally(error.getCause() != null ? error.getCause() : error);
            return failed;
        }
    }

    static BigDecimal decimal(Object value) {
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        if (value instanceof Number) {
            return new BigDecimal(value.toString());
        }
        throw new IllegalStateException("Currency provider returned a non-numeric balance: " + value);
    }
}
