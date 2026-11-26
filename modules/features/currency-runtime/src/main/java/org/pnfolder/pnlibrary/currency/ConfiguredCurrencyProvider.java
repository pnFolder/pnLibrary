package org.pnfolder.pnlibrary.currency;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount;
import org.pnfolder.pnlibrary.api.currency.CurrencyBalanceMutation;
import org.pnfolder.pnlibrary.api.currency.CurrencyCapability;
import org.pnfolder.pnlibrary.api.currency.CurrencyDeposits;
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor;
import org.pnfolder.pnlibrary.api.currency.CurrencyFormatting;
import org.pnfolder.pnlibrary.api.currency.CurrencyProvider;
import org.pnfolder.pnlibrary.api.currency.CurrencyReset;
import org.pnfolder.pnlibrary.api.currency.CurrencyResult;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransfers;
import org.pnfolder.pnlibrary.api.currency.CurrencyWithdrawals;

/** Immutable provider assembled from the operations selected by {@link CurrencyOperationsBuilder}. */
final class ConfiguredCurrencyProvider implements CurrencyProvider, CurrencyDeposits, CurrencyWithdrawals, CurrencyBalanceMutation,
        CurrencyReset, CurrencyTransfers, CurrencyFormatting, CurrencyCapabilitySource {
    private final CurrencyDescriptor descriptor;
    private final Function<CurrencyAccount, CompletionStage<BigDecimal>> balances;
    private final BiFunction<CurrencyAccount, BigDecimal, CompletionStage<CurrencyResult>> deposits;
    private final BiFunction<CurrencyAccount, BigDecimal, CompletionStage<CurrencyResult>> withdrawals;
    private final BiFunction<CurrencyAccount, BigDecimal, CompletionStage<CurrencyResult>> mutation;
    private final Function<CurrencyAccount, CompletionStage<CurrencyResult>> resets;
    private final AsyncTransfer transfers;
    private final Function<BigDecimal, String> formatting;
    private final Map<Class<?>, Object> extensions;
    private final Set<CurrencyCapability> declaredCapabilities;

    ConfiguredCurrencyProvider(@NotNull CurrencyDescriptor descriptor,
                               @NotNull Function<CurrencyAccount, CompletionStage<BigDecimal>> balances,
                               @Nullable BiFunction<CurrencyAccount, BigDecimal, CompletionStage<CurrencyResult>> deposits,
                               @Nullable BiFunction<CurrencyAccount, BigDecimal, CompletionStage<CurrencyResult>> withdrawals,
                               @Nullable BiFunction<CurrencyAccount, BigDecimal, CompletionStage<CurrencyResult>> mutation,
                               @Nullable Function<CurrencyAccount, CompletionStage<CurrencyResult>> resets,
                               @Nullable AsyncTransfer transfers, @Nullable Function<BigDecimal, String> formatting,
                               @NotNull Map<Class<?>, Object> extensions) {
        this.descriptor = descriptor;
        this.balances = balances;
        this.deposits = deposits;
        this.withdrawals = withdrawals;
        this.mutation = mutation;
        this.resets = resets;
        this.transfers = transfers;
        this.formatting = formatting;
        this.extensions = extensions;
        Set<CurrencyCapability> declared = new LinkedHashSet<CurrencyCapability>();
        declared.add(CurrencyCapability.BALANCE);
        if (deposits != null) {
            declared.add(CurrencyCapability.DEPOSIT);
        }
        if (withdrawals != null) {
            declared.add(CurrencyCapability.WITHDRAW);
        }
        if (mutation != null) {
            declared.add(CurrencyCapability.SET_BALANCE);
        }
        if (resets != null) {
            declared.add(CurrencyCapability.RESET);
        }
        if (transfers != null) {
            declared.add(CurrencyCapability.TRANSFER);
        }
        if (formatting != null) {
            declared.add(CurrencyCapability.FORMATTING);
        }
        this.declaredCapabilities = Collections.unmodifiableSet(declared);
    }

    @Override
    @NotNull
    public CurrencyDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    @NotNull
    public Set<CurrencyCapability> getDeclaredCapabilities() {
        return declaredCapabilities;
    }

    @Override
    @NotNull
    public CompletionStage<BigDecimal> balance(@NotNull CurrencyAccount account) {
        return balances.apply(account);
    }

    @Override
    @NotNull
    public CompletionStage<CurrencyResult> deposit(@NotNull CurrencyAccount account, @NotNull BigDecimal amount) {
        return deposits != null ? deposits.apply(account, amount) : unsupported();
    }

    @Override
    @NotNull
    public CompletionStage<CurrencyResult> withdraw(@NotNull CurrencyAccount account, @NotNull BigDecimal amount) {
        return withdrawals != null ? withdrawals.apply(account, amount) : unsupported();
    }

    @Override
    @NotNull
    public CompletionStage<CurrencyResult> setBalance(@NotNull CurrencyAccount account, @NotNull BigDecimal amount) {
        return mutation != null ? mutation.apply(account, amount) : unsupported();
    }

    @Override
    @NotNull
    public CompletionStage<CurrencyResult> reset(@NotNull CurrencyAccount account) {
        return resets != null ? resets.apply(account) : unsupported();
    }

    @Override
    @NotNull
    public CompletionStage<CurrencyResult> transfer(@NotNull CurrencyAccount from, @NotNull CurrencyAccount to,
                                                    @NotNull BigDecimal amount) {
        return transfers != null ? transfers.apply(from, to, amount) : unsupported();
    }

    @Override
    @NotNull
    public String format(@NotNull BigDecimal amount) {
        return formatting != null ? formatting.apply(amount) : descriptor.normalize(amount).toPlainString() + descriptor.getSymbol();
    }

    @Override
    @Nullable
    public <T> T extension(@NotNull Class<T> type) {
        Object value = extensions.get(type);
        return value == null ? null : type.cast(value);
    }

    /** Result returned when the plugin did not configure the requested operation. */
    private static CompletionStage<CurrencyResult> unsupported() {
        return CompletableFuture.completedFuture(CurrencyResult.unsupported("Operation is not configured"));
    }
}
