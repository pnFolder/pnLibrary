package org.pnfolder.pnlibrary.currency;

import java.util.Objects;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount;
import org.pnfolder.pnlibrary.api.currency.CurrencyActor;
import org.pnfolder.pnlibrary.api.currency.CurrencyBalanceMutation;
import org.pnfolder.pnlibrary.api.currency.CurrencyCommandSettings;
import org.pnfolder.pnlibrary.api.currency.CurrencyDeposits;
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor;
import org.pnfolder.pnlibrary.api.currency.CurrencyHistoryPage;
import org.pnfolder.pnlibrary.api.currency.CurrencyHistoryQuery;
import org.pnfolder.pnlibrary.api.currency.CurrencyKey;
import org.pnfolder.pnlibrary.api.currency.CurrencyLedger;
import org.pnfolder.pnlibrary.api.currency.CurrencyProvider;
import org.pnfolder.pnlibrary.api.currency.CurrencyRejectReason;
import org.pnfolder.pnlibrary.api.currency.CurrencyReset;
import org.pnfolder.pnlibrary.api.currency.CurrencyResult;
import org.pnfolder.pnlibrary.api.currency.CurrencyStorage;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransaction;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionRequest;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionStatus;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransactionType;
import org.pnfolder.pnlibrary.api.currency.CurrencyTransfers;
import org.pnfolder.pnlibrary.api.currency.CurrencyWithdrawals;

/** Storage-backed provider that validates requests before crossing the persistence boundary. */
final class ManagedCurrencyProvider implements CurrencyProvider, CurrencyDeposits, CurrencyWithdrawals, CurrencyBalanceMutation,
        CurrencyReset, CurrencyTransfers, CurrencyLedger, AutoCloseable {
    private final CurrencyKey key;
    private final CurrencyDescriptor descriptor;
    private final CurrencyStorage storage;
    private final String service;
    private final boolean ownsStorage;
    private final CurrencyCommandSettings commandSettings;

    ManagedCurrencyProvider(@NotNull CurrencyKey key, @NotNull CurrencyDescriptor descriptor, @NotNull CurrencyStorage storage,
                            @NotNull String service, boolean ownsStorage, @NotNull CurrencyCommandSettings commandSettings) {
        this.key = Objects.requireNonNull(key, "key");
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.storage = Objects.requireNonNull(storage, "storage");
        this.service = Objects.requireNonNull(service, "service");
        this.ownsStorage = ownsStorage;
        this.commandSettings = Objects.requireNonNull(commandSettings, "commandSettings");
    }

    @Override
    @NotNull
    public CurrencyDescriptor getDescriptor() {
        return descriptor;
    }

    @Override
    @NotNull
    public CompletionStage<BigDecimal> balance(@NotNull CurrencyAccount account) {
        return storage.balance(key, account);
    }

    @Override
    @NotNull
    public CompletionStage<CurrencyResult> deposit(@NotNull CurrencyAccount account, @NotNull BigDecimal amount) {
        return mutate(CurrencyTransactionType.CREDIT, amount, null, account, "API credit");
    }

    @Override
    @NotNull
    public CompletionStage<CurrencyResult> withdraw(@NotNull CurrencyAccount account, @NotNull BigDecimal amount) {
        return mutate(CurrencyTransactionType.DEBIT, amount, account, null, "API debit");
    }

    @Override
    @NotNull
    public CompletionStage<CurrencyResult> setBalance(@NotNull CurrencyAccount account, @NotNull BigDecimal amount) {
        return mutate(CurrencyTransactionType.SET_BALANCE, amount, null, account, "API balance update");
    }

    @Override
    @NotNull
    public CompletionStage<CurrencyResult> reset(@NotNull CurrencyAccount account) {
        return mutate(CurrencyTransactionType.RESET, BigDecimal.ZERO, null, account, "API balance reset");
    }

    @Override
    @NotNull
    public CompletionStage<CurrencyResult> transfer(@NotNull CurrencyAccount from, @NotNull CurrencyAccount to,
                                                    @NotNull BigDecimal amount) {
        return mutate(CurrencyTransactionType.TRANSFER, amount, from, to, "API transfer");
    }

    @Override
    @NotNull
    public CompletionStage<CurrencyTransaction> transact(@NotNull CurrencyTransactionRequest request) {
        String failure = CurrencyRequestValidation.failure(descriptor, request);
        if (failure == null) {
            return storage.transact(key, descriptor, request);
        }
        return CompletableFuture.completedFuture(rejectedTransaction(request, failure));
    }

    @Override
    @NotNull
    public CompletionStage<CurrencyHistoryPage> history(@NotNull CurrencyHistoryQuery query) {
        return storage.history(key, query);
    }

    @Override
    @Nullable
    public <T> T extension(@NotNull Class<T> type) {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        return type.isInstance(commandSettings) ? type.cast(commandSettings) : null;
    }

    @Override
    public void close() {
        if (ownsStorage) {
            try {
                storage.close();
            } catch (RuntimeException error) {
                throw error;
            } catch (Exception error) {
                throw new IllegalStateException(error);
            }
        }
    }

    /** Builds the rejected record returned when validation fails before storage is touched. */
    private CurrencyTransaction rejectedTransaction(CurrencyTransactionRequest request, String failure) {
        return new CurrencyTransaction(UUID.randomUUID(), key, request.getType(), CurrencyTransactionStatus.REJECTED,
                request.getAmount(), request.getSource(), request.getTarget(), request.getActor(), request.getService(),
                request.getReason(), request.getMetadata(), null, null, null, null, Instant.now(), failure,
                request.getIdempotencyKey());
    }

    /** Submits a system-initiated transaction for the simple API operations and maps the outcome to a result. */
    private CompletionStage<CurrencyResult> mutate(CurrencyTransactionType type, BigDecimal amount, CurrencyAccount source,
                                                   CurrencyAccount target, String reason) {
        return transact(new CurrencyTransactionRequest(type, amount, source, target, CurrencyActor.system("currency-api"), service,
                reason, java.util.Collections.emptyMap(), null)).thenApply(ManagedCurrencyProvider::toResult);
    }

    /** Maps a stored transaction to the consumer-facing result. */
    private static CurrencyResult toResult(CurrencyTransaction transaction) {
        switch (transaction.getStatus()) {
            case COMMITTED:
                return CurrencyResult.success(
                        transaction.getSourceBalanceBefore() != null ? transaction.getSourceBalanceBefore() : transaction.getTargetBalanceBefore(),
                        transaction.getSourceBalanceAfter() != null ? transaction.getSourceBalanceAfter() : transaction.getTargetBalanceAfter());
            case REJECTED:
                return CurrencyResult.rejected(rejectReason(transaction), transaction.getFailure());
            default:
                return CurrencyResult.failed(new IllegalStateException(
                        transaction.getFailure() != null ? transaction.getFailure() : "Currency transaction failed"));
        }
    }

    /** Insufficient funds is reported distinctly; any other rejection is a provider rejection. */
    private static CurrencyRejectReason rejectReason(CurrencyTransaction transaction) {
        return CurrencyMutationEngine.INSUFFICIENT_FUNDS.equals(transaction.getFailure())
                ? CurrencyRejectReason.INSUFFICIENT_FUNDS : CurrencyRejectReason.PROVIDER_REJECTED;
    }
}
