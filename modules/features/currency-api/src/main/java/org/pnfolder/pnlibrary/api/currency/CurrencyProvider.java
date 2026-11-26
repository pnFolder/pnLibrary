package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.concurrent.CompletionStage;

/**
 * Minimum contract implemented by every currency backend.
 *
 * <p>Balance lookup is mandatory. Mutating and presentation operations are exposed through optional extension
 * interfaces such as {@link CurrencyDeposits} and {@link CurrencyFormatting}.
 */
public interface CurrencyProvider {
    /** Human-readable metadata and decimal rules for this currency. */
    @NotNull
    CurrencyDescriptor getDescriptor();

    /** Asynchronously returns the current normalized balance of {@code account}. */
    @NotNull
    CompletionStage<BigDecimal> balance(@NotNull CurrencyAccount account);

    /** Returns an optional provider-specific or standard capability of exact {@code type}. */
    @Nullable
    default <T> T extension(@NotNull Class<T> type) {
        return null;
    }
}
