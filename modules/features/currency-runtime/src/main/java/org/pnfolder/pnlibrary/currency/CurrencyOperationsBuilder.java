package org.pnfolder.pnlibrary.currency;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;
import java.util.function.Function;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount;
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor;
import org.pnfolder.pnlibrary.api.currency.CurrencyOperations;
import org.pnfolder.pnlibrary.api.currency.CurrencyProvider;
import org.pnfolder.pnlibrary.api.currency.CurrencyResult;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransferOperation;

/** Adapts Java-friendly operation callbacks into an immutable currency provider. */
final class CurrencyOperationsBuilder implements CurrencyOperations {
    private Function<CurrencyAccount, CompletionStage<BigDecimal>> balance;
    private BiFunction<CurrencyAccount, BigDecimal, CompletionStage<CurrencyResult>> deposit;
    private BiFunction<CurrencyAccount, BigDecimal, CompletionStage<CurrencyResult>> withdraw;
    private BiFunction<CurrencyAccount, BigDecimal, CompletionStage<CurrencyResult>> setBalance;
    private Function<CurrencyAccount, CompletionStage<CurrencyResult>> reset;
    private AsyncTransfer transfer;
    private Function<BigDecimal, String> formatter;
    private final Map<Class<?>, Object> extensions = new LinkedHashMap<Class<?>, Object>();

    @Override
    @NotNull
    public CurrencyOperationsBuilder balance(@NotNull Function<CurrencyAccount, BigDecimal> operation) {
        balance = account -> completed(() -> operation.apply(account));
        return this;
    }

    @Override
    @NotNull
    public CurrencyOperationsBuilder balanceAsync(@NotNull Function<CurrencyAccount, CompletionStage<BigDecimal>> operation) {
        balance = operation;
        return this;
    }

    @Override
    @NotNull
    public CurrencyOperationsBuilder deposit(@NotNull BiFunction<CurrencyAccount, BigDecimal, CurrencyResult> operation) {
        deposit = (account, amount) -> completed(() -> operation.apply(account, amount));
        return this;
    }

    @Override
    @NotNull
    public CurrencyOperationsBuilder depositAsync(@NotNull BiFunction<CurrencyAccount, BigDecimal, CompletionStage<CurrencyResult>> operation) {
        deposit = operation;
        return this;
    }

    @Override
    @NotNull
    public CurrencyOperationsBuilder withdraw(@NotNull BiFunction<CurrencyAccount, BigDecimal, CurrencyResult> operation) {
        withdraw = (account, amount) -> completed(() -> operation.apply(account, amount));
        return this;
    }

    @Override
    @NotNull
    public CurrencyOperationsBuilder withdrawAsync(@NotNull BiFunction<CurrencyAccount, BigDecimal, CompletionStage<CurrencyResult>> operation) {
        withdraw = operation;
        return this;
    }

    @Override
    @NotNull
    public CurrencyOperationsBuilder setBalance(@NotNull BiFunction<CurrencyAccount, BigDecimal, CurrencyResult> operation) {
        setBalance = (account, amount) -> completed(() -> operation.apply(account, amount));
        return this;
    }

    @Override
    @NotNull
    public CurrencyOperationsBuilder reset(@NotNull Function<CurrencyAccount, CurrencyResult> operation) {
        reset = account -> completed(() -> operation.apply(account));
        return this;
    }

    @Override
    @NotNull
    public CurrencyOperationsBuilder transfer(@NotNull CurrencyTransferOperation operation) {
        transfer = (from, to, amount) -> completed(() -> operation.apply(from, to, amount));
        return this;
    }

    @Override
    @NotNull
    public CurrencyOperationsBuilder format(@NotNull Function<BigDecimal, String> operation) {
        formatter = operation;
        return this;
    }

    @Override
    @NotNull
    public CurrencyOperationsBuilder extension(@NotNull Class<?> type, @NotNull Object value) {
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("Extension value is not an instance of " + type.getName());
        }
        extensions.put(type, value);
        return this;
    }

    /** Creates a provider and verifies that the mandatory balance operation exists. */
    @NotNull
    CurrencyProvider build(@NotNull CurrencyDescriptor descriptor, @NotNull String name) {
        if (balance == null) {
            throw new IllegalStateException("Currency " + name + " requires a balance operation");
        }
        return new ConfiguredCurrencyProvider(descriptor, balance, deposit, withdraw, setBalance, reset, transfer, formatter,
                new LinkedHashMap<Class<?>, Object>(extensions));
    }

    /** Wraps a synchronous callback result, or its exception, into an already completed stage. */
    private static <T> CompletionStage<T> completed(Callable<T> operation) {
        try {
            return CompletableFuture.completedFuture(operation.call());
        } catch (Throwable error) {
            CompletableFuture<T> failed = new CompletableFuture<T>();
            failed.completeExceptionally(new IllegalStateException("Currency operation failed", error));
            return failed;
        }
    }
}
