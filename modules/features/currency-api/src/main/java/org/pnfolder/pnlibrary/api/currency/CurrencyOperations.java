package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;

/** Java-friendly operation builder for a lightweight currency provider. */
public interface CurrencyOperations {
    /** Configures the mandatory synchronous balance lookup. */
    @NotNull
    CurrencyOperations balance(@NotNull Function<CurrencyAccount, BigDecimal> operation);

    /** Configures the mandatory asynchronous balance lookup. */
    @NotNull
    CurrencyOperations balanceAsync(@NotNull Function<CurrencyAccount, CompletionStage<BigDecimal>> operation);

    /** Configures synchronous deposit support. */
    @NotNull
    CurrencyOperations deposit(@NotNull BiFunction<CurrencyAccount, BigDecimal, CurrencyResult> operation);

    /** Configures asynchronous deposit support. */
    @NotNull
    CurrencyOperations depositAsync(
            @NotNull BiFunction<CurrencyAccount, BigDecimal, CompletionStage<CurrencyResult>> operation);

    /** Configures synchronous withdrawal support. */
    @NotNull
    CurrencyOperations withdraw(@NotNull BiFunction<CurrencyAccount, BigDecimal, CurrencyResult> operation);

    /** Configures asynchronous withdrawal support. */
    @NotNull
    CurrencyOperations withdrawAsync(
            @NotNull BiFunction<CurrencyAccount, BigDecimal, CompletionStage<CurrencyResult>> operation);

    /** Configures synchronous set-balance support. */
    @NotNull
    CurrencyOperations setBalance(@NotNull BiFunction<CurrencyAccount, BigDecimal, CurrencyResult> operation);

    /** Configures synchronous reset support. */
    @NotNull
    CurrencyOperations reset(@NotNull Function<CurrencyAccount, CurrencyResult> operation);

    /** Configures synchronous atomic transfer support. */
    @NotNull
    CurrencyOperations transfer(@NotNull CurrencyTransferOperation operation);

    /** Configures provider-specific amount presentation. */
    @NotNull
    CurrencyOperations format(@NotNull Function<BigDecimal, String> operation);

    /** Publishes {@code value} as an advanced extension under exact {@code type}. */
    @NotNull
    CurrencyOperations extension(@NotNull Class<?> type, @NotNull Object value);
}
