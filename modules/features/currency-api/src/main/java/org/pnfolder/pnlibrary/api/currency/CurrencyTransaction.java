package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable ledger record produced for a transaction request.
 *
 * <p>Before/after balances are populated only for accounts involved in the operation. {@link #getFailure()} explains a
 * rejected or failed outcome and must not be used as a stable machine-readable reason. Use {@link #toBuilder()} to
 * derive a changed copy.
 */
public final class CurrencyTransaction {
    private final UUID id;
    private final CurrencyKey currency;
    private final CurrencyTransactionType type;
    private final CurrencyTransactionStatus status;
    private final BigDecimal amount;
    private final CurrencyAccount source;
    private final CurrencyAccount target;
    private final CurrencyActor actor;
    private final String service;
    private final String reason;
    private final Map<String, String> metadata;
    private final BigDecimal sourceBalanceBefore;
    private final BigDecimal sourceBalanceAfter;
    private final BigDecimal targetBalanceBefore;
    private final BigDecimal targetBalanceAfter;
    private final Instant createdAt;
    private final String failure;
    private final String idempotencyKey;

    private CurrencyTransaction(Builder b) {
        this(b.id, b.currency, b.type, b.status, b.amount, b.source, b.target, b.actor, b.service, b.reason,
                b.metadata, b.sourceBalanceBefore, b.sourceBalanceAfter, b.targetBalanceBefore, b.targetBalanceAfter,
                b.createdAt, b.failure, b.idempotencyKey);
    }

    /** Creates a record; the balances and the optional values may be {@code null}. */
    public CurrencyTransaction(@NotNull UUID id, @NotNull CurrencyKey currency, @NotNull CurrencyTransactionType type,
                               @NotNull CurrencyTransactionStatus status, @NotNull BigDecimal amount,
                               @Nullable CurrencyAccount source, @Nullable CurrencyAccount target,
                               @NotNull CurrencyActor actor, @NotNull String service, @Nullable String reason,
                               @NotNull Map<String, String> metadata, @Nullable BigDecimal sourceBalanceBefore,
                               @Nullable BigDecimal sourceBalanceAfter, @Nullable BigDecimal targetBalanceBefore,
                               @Nullable BigDecimal targetBalanceAfter, @NotNull Instant createdAt,
                               @Nullable String failure, @Nullable String idempotencyKey) {
        this.id = Objects.requireNonNull(id, "id");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.type = Objects.requireNonNull(type, "type");
        this.status = Objects.requireNonNull(status, "status");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.source = source;
        this.target = target;
        this.actor = Objects.requireNonNull(actor, "actor");
        this.service = Objects.requireNonNull(service, "service");
        this.reason = reason;
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
        this.sourceBalanceBefore = sourceBalanceBefore;
        this.sourceBalanceAfter = sourceBalanceAfter;
        this.targetBalanceBefore = targetBalanceBefore;
        this.targetBalanceAfter = targetBalanceAfter;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.failure = failure;
        this.idempotencyKey = idempotencyKey;
    }

    /** Creates an empty builder; every required value must be set before {@link Builder#build()}. */
    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    /** Creates a builder initialized with the values of this record. */
    @NotNull
    public Builder toBuilder() {
        Builder b = new Builder();
        b.id = id;
        b.currency = currency;
        b.type = type;
        b.status = status;
        b.amount = amount;
        b.source = source;
        b.target = target;
        b.actor = actor;
        b.service = service;
        b.reason = reason;
        b.metadata = metadata;
        b.sourceBalanceBefore = sourceBalanceBefore;
        b.sourceBalanceAfter = sourceBalanceAfter;
        b.targetBalanceBefore = targetBalanceBefore;
        b.targetBalanceAfter = targetBalanceAfter;
        b.createdAt = createdAt;
        b.failure = failure;
        b.idempotencyKey = idempotencyKey;
        return b;
    }

    /** Globally unique ledger-record identifier. */
    @NotNull
    public UUID getId() {
        return id;
    }

    /** Currency whose balance was addressed. */
    @NotNull
    public CurrencyKey getCurrency() {
        return currency;
    }

    /** Mutation requested by the caller. */
    @NotNull
    public CurrencyTransactionType getType() {
        return type;
    }

    /** Persisted outcome of the request. */
    @NotNull
    public CurrencyTransactionStatus getStatus() {
        return status;
    }

    /** Normalized amount used by the operation. */
    @NotNull
    public BigDecimal getAmount() {
        return amount;
    }

    /** Debited account, when the operation has one. */
    @Nullable
    public CurrencyAccount getSource() {
        return source;
    }

    /** Credited or updated account, when the operation has one. */
    @Nullable
    public CurrencyAccount getTarget() {
        return target;
    }

    /** Principal that initiated the request. */
    @NotNull
    public CurrencyActor getActor() {
        return actor;
    }

    /** Application subsystem responsible for the request. */
    @NotNull
    public String getService() {
        return service;
    }

    /** Optional human-readable audit explanation. */
    @Nullable
    public String getReason() {
        return reason;
    }

    /** Immutable caller-provided audit attributes. */
    @NotNull
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /** Source balance immediately before mutation. */
    @Nullable
    public BigDecimal getSourceBalanceBefore() {
        return sourceBalanceBefore;
    }

    /** Source balance immediately after mutation. */
    @Nullable
    public BigDecimal getSourceBalanceAfter() {
        return sourceBalanceAfter;
    }

    /** Target balance immediately before mutation. */
    @Nullable
    public BigDecimal getTargetBalanceBefore() {
        return targetBalanceBefore;
    }

    /** Target balance immediately after mutation. */
    @Nullable
    public BigDecimal getTargetBalanceAfter() {
        return targetBalanceAfter;
    }

    /** Instant at which storage recorded the outcome. */
    @NotNull
    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Human-readable rejection or infrastructure failure detail. */
    @Nullable
    public String getFailure() {
        return failure;
    }

    /** Retry identity supplied with the original request. */
    @Nullable
    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CurrencyTransaction)) {
            return false;
        }
        CurrencyTransaction that = (CurrencyTransaction) other;
        return id.equals(that.id) && currency.equals(that.currency) && type == that.type && status == that.status
                && amount.equals(that.amount) && Objects.equals(source, that.source)
                && Objects.equals(target, that.target) && actor.equals(that.actor) && service.equals(that.service)
                && Objects.equals(reason, that.reason) && metadata.equals(that.metadata)
                && Objects.equals(sourceBalanceBefore, that.sourceBalanceBefore)
                && Objects.equals(sourceBalanceAfter, that.sourceBalanceAfter)
                && Objects.equals(targetBalanceBefore, that.targetBalanceBefore)
                && Objects.equals(targetBalanceAfter, that.targetBalanceAfter)
                && createdAt.equals(that.createdAt) && Objects.equals(failure, that.failure)
                && Objects.equals(idempotencyKey, that.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, currency, type, status, amount, source, target, actor, service, reason, metadata,
                sourceBalanceBefore, sourceBalanceAfter, targetBalanceBefore, targetBalanceAfter, createdAt,
                failure, idempotencyKey);
    }

    @Override
    public String toString() {
        return "CurrencyTransaction(id=" + id + ", currency=" + currency + ", type=" + type + ", status=" + status
                + ", amount=" + amount + ", source=" + source + ", target=" + target + ", actor=" + actor
                + ", service=" + service + ", reason=" + reason + ", metadata=" + metadata
                + ", sourceBalanceBefore=" + sourceBalanceBefore + ", sourceBalanceAfter=" + sourceBalanceAfter
                + ", targetBalanceBefore=" + targetBalanceBefore + ", targetBalanceAfter=" + targetBalanceAfter
                + ", createdAt=" + createdAt + ", failure=" + failure + ", idempotencyKey=" + idempotencyKey + ")";
    }

    /** Mutable builder for {@link CurrencyTransaction}; required values are checked by {@link #build()}. */
    public static final class Builder {
        private UUID id;
        private CurrencyKey currency;
        private CurrencyTransactionType type;
        private CurrencyTransactionStatus status;
        private BigDecimal amount;
        private CurrencyAccount source;
        private CurrencyAccount target;
        private CurrencyActor actor;
        private String service;
        private String reason;
        private Map<String, String> metadata = Collections.emptyMap();
        private BigDecimal sourceBalanceBefore;
        private BigDecimal sourceBalanceAfter;
        private BigDecimal targetBalanceBefore;
        private BigDecimal targetBalanceAfter;
        private Instant createdAt;
        private String failure;
        private String idempotencyKey;

        Builder() {
        }

        /** Sets the ledger-record identifier. */
        @NotNull
        public Builder id(@NotNull UUID value) {
            id = value;
            return this;
        }

        /** Sets the currency whose balance was addressed. */
        @NotNull
        public Builder currency(@NotNull CurrencyKey value) {
            currency = value;
            return this;
        }

        /** Sets the requested mutation. */
        @NotNull
        public Builder type(@NotNull CurrencyTransactionType value) {
            type = value;
            return this;
        }

        /** Sets the persisted outcome. */
        @NotNull
        public Builder status(@NotNull CurrencyTransactionStatus value) {
            status = value;
            return this;
        }

        /** Sets the normalized amount. */
        @NotNull
        public Builder amount(@NotNull BigDecimal value) {
            amount = value;
            return this;
        }

        /** Sets the debited account. */
        @NotNull
        public Builder source(@Nullable CurrencyAccount value) {
            source = value;
            return this;
        }

        /** Sets the credited or updated account. */
        @NotNull
        public Builder target(@Nullable CurrencyAccount value) {
            target = value;
            return this;
        }

        /** Sets the initiating principal. */
        @NotNull
        public Builder actor(@NotNull CurrencyActor value) {
            actor = value;
            return this;
        }

        /** Sets the responsible application subsystem. */
        @NotNull
        public Builder service(@NotNull String value) {
            service = value;
            return this;
        }

        /** Sets the audit explanation. */
        @NotNull
        public Builder reason(@Nullable String value) {
            reason = value;
            return this;
        }

        /** Sets the audit attributes. */
        @NotNull
        public Builder metadata(@NotNull Map<String, String> value) {
            metadata = value;
            return this;
        }

        /** Sets the source balance before and after the mutation. */
        @NotNull
        public Builder sourceBalance(@Nullable BigDecimal before, @Nullable BigDecimal after) {
            sourceBalanceBefore = before;
            sourceBalanceAfter = after;
            return this;
        }

        /** Sets the target balance before and after the mutation. */
        @NotNull
        public Builder targetBalance(@Nullable BigDecimal before, @Nullable BigDecimal after) {
            targetBalanceBefore = before;
            targetBalanceAfter = after;
            return this;
        }

        /** Sets the instant at which storage recorded the outcome. */
        @NotNull
        public Builder createdAt(@NotNull Instant value) {
            createdAt = value;
            return this;
        }

        /** Sets the rejection or failure detail. */
        @NotNull
        public Builder failure(@Nullable String value) {
            failure = value;
            return this;
        }

        /** Sets the retry identity of the original request. */
        @NotNull
        public Builder idempotencyKey(@Nullable String value) {
            idempotencyKey = value;
            return this;
        }

        /** Creates the immutable record; fails when a required value is missing. */
        @NotNull
        public CurrencyTransaction build() {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(currency, "currency");
            Objects.requireNonNull(type, "type");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(actor, "actor");
            Objects.requireNonNull(service, "service");
            Objects.requireNonNull(createdAt, "createdAt");
            return new CurrencyTransaction(this);
        }
    }
}
