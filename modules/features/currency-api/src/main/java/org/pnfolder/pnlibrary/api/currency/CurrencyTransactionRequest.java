package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Validated intent submitted to {@link CurrencyStorage#transact}.
 *
 * <p>Storage implementations validate the account combination required by {@link #getType()}. A non-null
 * {@link #getIdempotencyKey()} identifies one logical request within a currency: retries return the already persisted
 * transaction instead of applying the balance change twice.
 */
public final class CurrencyTransactionRequest {
    private final CurrencyTransactionType type;
    private final BigDecimal amount;
    private final CurrencyAccount source;
    private final CurrencyAccount target;
    private final CurrencyActor actor;
    private final String service;
    private final String reason;
    private final Map<String, String> metadata;
    private final String idempotencyKey;

    /** Creates a request without reason, metadata and idempotency key. */
    public CurrencyTransactionRequest(@NotNull CurrencyTransactionType type, @NotNull BigDecimal amount,
                                      @Nullable CurrencyAccount source, @Nullable CurrencyAccount target,
                                      @NotNull CurrencyActor actor, @NotNull String service) {
        this(type, amount, source, target, actor, service, null, Collections.<String, String>emptyMap(), null);
    }

    /**
     * Creates a request.
     *
     * @param type requested balance mutation
     * @param amount amount interpreted according to the currency descriptor
     * @param source debited account for debit and transfer operations
     * @param target credited or replaced account for credit, transfer, set, and reset
     * @param actor principal responsible for the request
     * @param service application operation or subsystem producing the request
     * @param reason optional human-readable audit reason, limited to 512 characters
     * @param metadata structured audit values, limited to 64 entries
     * @param idempotencyKey optional retry identity, nonblank and at most 128 characters
     */
    public CurrencyTransactionRequest(@NotNull CurrencyTransactionType type, @NotNull BigDecimal amount,
                                      @Nullable CurrencyAccount source, @Nullable CurrencyAccount target,
                                      @NotNull CurrencyActor actor, @NotNull String service, @Nullable String reason,
                                      @NotNull Map<String, String> metadata, @Nullable String idempotencyKey) {
        if (CurrencyText.isBlank(service)) {
            throw new IllegalArgumentException("Currency transaction service must not be blank");
        }
        if (metadata.size() > 64) {
            throw new IllegalArgumentException("Currency transaction metadata cannot contain more than 64 entries");
        }
        if (service.length() > 128) {
            throw new IllegalArgumentException("Currency transaction service is too long");
        }
        if (reason != null && reason.length() > 512) {
            throw new IllegalArgumentException("Currency transaction reason is too long");
        }
        if (idempotencyKey != null && (CurrencyText.isBlank(idempotencyKey) || idempotencyKey.length() > 128)) {
            throw new IllegalArgumentException("Invalid currency idempotency key");
        }
        for (Map.Entry<String, String> entry : metadata.entrySet()) {
            if (CurrencyText.isBlank(entry.getKey()) || entry.getKey().length() > 64 || entry.getValue().length() > 512) {
                throw new IllegalArgumentException("Invalid currency transaction metadata");
            }
        }
        this.type = type;
        this.amount = amount;
        this.source = source;
        this.target = target;
        this.actor = actor;
        this.service = service;
        this.reason = reason;
        this.metadata = Collections.unmodifiableMap(new LinkedHashMap<String, String>(metadata));
        this.idempotencyKey = idempotencyKey;
    }

    /** Requested balance mutation. */
    @NotNull
    public CurrencyTransactionType getType() {
        return type;
    }

    /** Amount interpreted according to the currency descriptor. */
    @NotNull
    public BigDecimal getAmount() {
        return amount;
    }

    /** Debited account for debit and transfer operations. */
    @Nullable
    public CurrencyAccount getSource() {
        return source;
    }

    /** Credited or replaced account for credit, transfer, set, and reset. */
    @Nullable
    public CurrencyAccount getTarget() {
        return target;
    }

    /** Principal responsible for the request. */
    @NotNull
    public CurrencyActor getActor() {
        return actor;
    }

    /** Application operation or subsystem producing the request. */
    @NotNull
    public String getService() {
        return service;
    }

    /** Optional human-readable audit reason. */
    @Nullable
    public String getReason() {
        return reason;
    }

    /** Structured audit values. */
    @NotNull
    public Map<String, String> getMetadata() {
        return metadata;
    }

    /** Optional retry identity. */
    @Nullable
    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    /** Returns a copy of this request with another idempotency key. */
    @NotNull
    public CurrencyTransactionRequest withIdempotencyKey(@Nullable String idempotencyKey) {
        return new CurrencyTransactionRequest(type, amount, source, target, actor, service, reason, metadata, idempotencyKey);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CurrencyTransactionRequest)) {
            return false;
        }
        CurrencyTransactionRequest that = (CurrencyTransactionRequest) other;
        return type == that.type && amount.equals(that.amount) && Objects.equals(source, that.source)
                && Objects.equals(target, that.target) && actor.equals(that.actor) && service.equals(that.service)
                && Objects.equals(reason, that.reason) && metadata.equals(that.metadata)
                && Objects.equals(idempotencyKey, that.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, amount, source, target, actor, service, reason, metadata, idempotencyKey);
    }

    @Override
    public String toString() {
        return "CurrencyTransactionRequest(type=" + type + ", amount=" + amount + ", source=" + source
                + ", target=" + target + ", actor=" + actor + ", service=" + service + ", reason=" + reason
                + ", metadata=" + metadata + ", idempotencyKey=" + idempotencyKey + ")";
    }
}
