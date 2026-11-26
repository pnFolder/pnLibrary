package org.pnfolder.pnlibrary.currency;

import java.util.Objects;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount;
import org.pnfolder.pnlibrary.api.currency.CurrencyBalanceMutation;
import org.pnfolder.pnlibrary.api.currency.CurrencyCapability;
import org.pnfolder.pnlibrary.api.currency.CurrencyDeposits;
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor;
import org.pnfolder.pnlibrary.api.currency.CurrencyFormatting;
import org.pnfolder.pnlibrary.api.currency.CurrencyKey;
import org.pnfolder.pnlibrary.api.currency.CurrencyProvider;
import org.pnfolder.pnlibrary.api.currency.CurrencyRegistration;
import org.pnfolder.pnlibrary.api.currency.CurrencyRegistrationState;
import org.pnfolder.pnlibrary.api.currency.CurrencyRejectReason;
import org.pnfolder.pnlibrary.api.currency.CurrencyReset;
import org.pnfolder.pnlibrary.api.currency.CurrencyResult;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransfers;
import org.pnfolder.pnlibrary.api.currency.CurrencyWithdrawals;
import org.pnfolder.pnlibrary.common.lang.Failures;

/** One registered currency: wraps the provider with state, capability checks and amount normalization. */
final class RegisteredCurrency implements CurrencyRegistration {
    private final CurrencyHub hub;
    private final CurrencyKey key;
    private final CurrencyProvider provider;
    private final Set<String> registeredAliases;
    private final List<AutoCloseable> attachments = Collections.synchronizedList(new ArrayList<>());
    private final AtomicBoolean enabled = new AtomicBoolean(true);
    private final AtomicBoolean entryClosed = new AtomicBoolean(false);
    private final Set<CurrencyCapability> capabilities;

    RegisteredCurrency(@NotNull CurrencyHub hub, @NotNull CurrencyKey key, @NotNull CurrencyProvider provider,
                       @NotNull Set<String> registeredAliases) {
        this.hub = Objects.requireNonNull(hub, "hub");
        this.key = Objects.requireNonNull(key, "key");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.registeredAliases = Objects.requireNonNull(registeredAliases, "registeredAliases");
        this.capabilities = CurrencyCapabilities.of(provider);
    }

    @Override
    @NotNull
    public CurrencyKey getKey() {
        return key;
    }

    @Override
    @NotNull
    public CurrencyDescriptor getDescriptor() {
        return provider.getDescriptor();
    }

    @Override
    @NotNull
    public CurrencyRegistrationState getState() {
        if (entryClosed.get()) {
            return CurrencyRegistrationState.CLOSED;
        }
        return enabled.get() ? CurrencyRegistrationState.ACTIVE : CurrencyRegistrationState.DISABLED;
    }

    @Override
    @NotNull
    public Set<CurrencyCapability> getCapabilities() {
        return capabilities;
    }

    @Override
    @NotNull
    public CompletionStage<BigDecimal> balance(@NotNull UUID playerId) {
        return availableValue(() -> provider.balance(new CurrencyAccount(playerId)).thenApply(provider.getDescriptor()::normalize));
    }

    @Override
    @NotNull
    public CompletionStage<Boolean> has(@NotNull UUID playerId, @NotNull BigDecimal amount) {
        if (!acceptsBalance(amount)) {
            return CompletableFuture.completedFuture(Boolean.FALSE);
        }
        return balance(playerId).thenApply(balance -> balance.compareTo(provider.getDescriptor().normalize(amount)) >= 0);
    }

    @Override
    @NotNull
    public CompletionStage<CurrencyResult> deposit(@NotNull UUID playerId, @NotNull BigDecimal amount) {
        return amountOperation(CurrencyCapability.DEPOSIT, amount, this::acceptsAmount,
                normalized -> ((CurrencyDeposits) provider).deposit(new CurrencyAccount(playerId), normalized));
    }

    @Override
    @NotNull
    public CompletionStage<CurrencyResult> withdraw(@NotNull UUID playerId, @NotNull BigDecimal amount) {
        return amountOperation(CurrencyCapability.WITHDRAW, amount, this::acceptsAmount,
                normalized -> ((CurrencyWithdrawals) provider).withdraw(new CurrencyAccount(playerId), normalized));
    }

    @Override
    @NotNull
    public CompletionStage<CurrencyResult> setBalance(@NotNull UUID playerId, @NotNull BigDecimal amount) {
        return amountOperation(CurrencyCapability.SET_BALANCE, amount, this::acceptsBalance,
                normalized -> ((CurrencyBalanceMutation) provider).setBalance(new CurrencyAccount(playerId), normalized));
    }

    @Override
    @NotNull
    public CompletionStage<CurrencyResult> reset(@NotNull UUID playerId) {
        return resultOperation(CurrencyCapability.RESET, () -> ((CurrencyReset) provider).reset(new CurrencyAccount(playerId)));
    }

    @Override
    @NotNull
    public CompletionStage<CurrencyResult> transfer(@NotNull UUID from, @NotNull UUID to, @NotNull BigDecimal amount) {
        return amountOperation(CurrencyCapability.TRANSFER, amount, this::acceptsAmount,
                normalized -> ((CurrencyTransfers) provider).transfer(new CurrencyAccount(from), new CurrencyAccount(to), normalized));
    }

    @Override
    @NotNull
    public String format(@NotNull BigDecimal amount) {
        BigDecimal normalized = provider.getDescriptor().normalize(amount);
        if (provider instanceof CurrencyFormatting) {
            return ((CurrencyFormatting) provider).format(normalized);
        }
        return normalized.toPlainString() + provider.getDescriptor().getSymbol();
    }

    @Override
    @Nullable
    public <T> T extension(@NotNull Class<T> type) {
        return provider.extension(type);
    }

    @Override
    public void enable() {
        if (entryClosed.get()) {
            throw new IllegalStateException("Currency " + key + " is closed");
        }
        enabled.set(true);
    }

    @Override
    public void disable() {
        enabled.set(false);
    }

    @Override
    public void close() {
        if (!entryClosed.compareAndSet(false, true)) {
            return;
        }
        enabled.set(false);
        hub.forget(this, registeredAliases);
        List<AutoCloseable> toClose;
        synchronized (attachments) {
            toClose = new ArrayList<>(attachments);
            attachments.clear();
        }
        for (AutoCloseable attachment : toClose) {
            closeReporting(attachment);
        }
        if (provider instanceof AutoCloseable) {
            closeReporting((AutoCloseable) provider);
        }
    }

    private void closeReporting(AutoCloseable closeable) {
        Failures.runCatchingNonFatal(() -> {
            closeable.close();
            return null;
        }).onFailure(error -> hub.report("Currency " + key + " did not close cleanly", error));
    }

    /** Ties {@code registration} (for example a placeholder) to this entry so it is closed together with it. */
    void attach(@NotNull AutoCloseable registration) {
        attachments.add(registration);
    }

    /** Runs an amount-based mutation after state, capability and amount validation. */
    private CompletionStage<CurrencyResult> amountOperation(CurrencyCapability capability, BigDecimal amount,
                                                            Predicate<BigDecimal> accepts,
                                                            Function<BigDecimal, CompletionStage<CurrencyResult>> operation) {
        if (getState() != CurrencyRegistrationState.ACTIVE) {
            return unavailable();
        }
        if (!supports(capability)) {
            return unsupported(capability);
        }
        if (!accepts.test(amount)) {
            return invalidAmount();
        }
        return safeResult(() -> operation.apply(provider.getDescriptor().normalize(amount)));
    }

    /** Runs a mutation without an amount after state and capability validation. */
    private CompletionStage<CurrencyResult> resultOperation(CurrencyCapability capability,
                                                            Supplier<CompletionStage<CurrencyResult>> operation) {
        if (getState() != CurrencyRegistrationState.ACTIVE) {
            return unavailable();
        }
        if (!supports(capability)) {
            return unsupported(capability);
        }
        return safeResult(operation);
    }

    private boolean acceptsAmount(BigDecimal amount) {
        return provider.getDescriptor().accepts(amount) && amount.signum() > 0;
    }

    private boolean acceptsBalance(BigDecimal amount) {
        return provider.getDescriptor().accepts(amount) && amount.signum() >= 0;
    }

    private CompletionStage<CurrencyResult> unavailable() {
        return CompletableFuture.completedFuture(CurrencyResult.unavailable("Currency " + key + " is not active"));
    }

    private CompletionStage<CurrencyResult> unsupported(CurrencyCapability capability) {
        return CompletableFuture.completedFuture(CurrencyResult.unsupported("Currency " + key + " does not support " + capability));
    }

    private CompletionStage<CurrencyResult> invalidAmount() {
        return CompletableFuture.completedFuture(CurrencyResult.rejected(CurrencyRejectReason.INVALID_AMOUNT,
                "Amount must be positive and use at most " + provider.getDescriptor().getFractionDigits() + " fraction digits"));
    }

    private <T> CompletionStage<T> availableValue(Callable<CompletionStage<T>> operation) {
        if (getState() != CurrencyRegistrationState.ACTIVE) {
            return failed("Currency " + key + " is not active", null);
        }
        try {
            return operation.call().whenComplete((ignored, error) -> {
                if (error != null) {
                    hub.report("Currency " + key + " failed", error.getCause() != null ? error.getCause() : error);
                }
            });
        } catch (Throwable error) {
            hub.report("Currency " + key + " failed", error);
            return failed("Currency " + key + " failed", error);
        }
    }

    /** Converts provider exceptions, thrown or asynchronous, into a failed {@link CurrencyResult}. */
    private CompletionStage<CurrencyResult> safeResult(Supplier<CompletionStage<CurrencyResult>> operation) {
        try {
            return operation.get().exceptionally(error -> {
                hub.report("Currency " + key + " operation failed", error.getCause() != null ? error.getCause() : error);
                return CurrencyResult.failed(error);
            });
        } catch (Throwable error) {
            hub.report("Currency " + key + " operation failed", error);
            return CompletableFuture.completedFuture(CurrencyResult.failed(error));
        }
    }

    private static <T> CompletionStage<T> failed(String message, Throwable cause) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(new IllegalStateException(message, cause));
        return future;
    }
}
