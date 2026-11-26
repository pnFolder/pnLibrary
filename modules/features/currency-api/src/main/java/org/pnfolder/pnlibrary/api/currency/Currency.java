package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * Consumer-facing asynchronous facade for one registered currency.
 *
 * <p>Amount mutations reject values that violate {@link #getDescriptor()} precision. Deposit, withdrawal, and
 * transfer require positive amounts; set-balance accepts zero. Unsupported or inactive mutations complete normally
 * with a structured {@link CurrencyResult}.
 */
public interface Currency {
    /** Globally unique registration key. */
    @NotNull
    CurrencyKey getKey();

    /** Presentation and decimal rules applied by this facade. */
    @NotNull
    CurrencyDescriptor getDescriptor();

    /** Immutable set of operations supported by the underlying provider. */
    @NotNull
    Set<CurrencyCapability> getCapabilities();

    /** Returns whether the provider exposes {@code capability}. */
    default boolean supports(@NotNull CurrencyCapability capability) {
        return getCapabilities().contains(capability);
    }

    /** Returns a normalized balance for {@code playerId}. */
    @NotNull
    CompletionStage<BigDecimal> balance(@NotNull UUID playerId);

    /** Returns whether the normalized balance covers positive {@code amount}. */
    @NotNull
    CompletionStage<Boolean> has(@NotNull UUID playerId, @NotNull BigDecimal amount);

    /** Attempts to add positive {@code amount} to the player's balance. */
    @NotNull
    CompletionStage<CurrencyResult> deposit(@NotNull UUID playerId, @NotNull BigDecimal amount);

    /** Long convenience overload for {@link #deposit(UUID, BigDecimal)}. */
    @NotNull
    default CompletionStage<CurrencyResult> deposit(@NotNull UUID playerId, long amount) {
        return deposit(playerId, BigDecimal.valueOf(amount));
    }

    /** Attempts to subtract positive {@code amount} from the player's balance. */
    @NotNull
    CompletionStage<CurrencyResult> withdraw(@NotNull UUID playerId, @NotNull BigDecimal amount);

    /** Long convenience overload for {@link #withdraw(UUID, BigDecimal)}. */
    @NotNull
    default CompletionStage<CurrencyResult> withdraw(@NotNull UUID playerId, long amount) {
        return withdraw(playerId, BigDecimal.valueOf(amount));
    }

    /** Attempts to replace the player's balance with non-negative {@code amount}. */
    @NotNull
    CompletionStage<CurrencyResult> setBalance(@NotNull UUID playerId, @NotNull BigDecimal amount);

    /** Long convenience overload for {@link #setBalance(UUID, BigDecimal)}. */
    @NotNull
    default CompletionStage<CurrencyResult> setBalance(@NotNull UUID playerId, long amount) {
        return setBalance(playerId, BigDecimal.valueOf(amount));
    }

    /** Restores the player's balance through the provider reset capability. */
    @NotNull
    CompletionStage<CurrencyResult> reset(@NotNull UUID playerId);

    /** Attempts to atomically transfer positive {@code amount} between distinct players. */
    @NotNull
    CompletionStage<CurrencyResult> transfer(@NotNull UUID from, @NotNull UUID to, @NotNull BigDecimal amount);

    /** Long convenience overload for {@link #transfer(UUID, UUID, BigDecimal)}. */
    @NotNull
    default CompletionStage<CurrencyResult> transfer(@NotNull UUID from, @NotNull UUID to, long amount) {
        return transfer(from, to, BigDecimal.valueOf(amount));
    }

    /** Formats normalized {@code amount} using provider formatting or descriptor defaults. */
    @NotNull
    String format(@NotNull BigDecimal amount);

    /** Returns an optional advanced contract registered under exact {@code type}. */
    @Nullable
    <T> T extension(@NotNull Class<T> type);
}
