package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Filters and paginates reverse-chronological ledger history.
 *
 * <p>All non-null filters are combined with logical AND. {@link #getFrom()} is inclusive and {@link #getUntil()} is
 * exclusive, making adjacent time ranges safe to concatenate without duplicates. Empty {@link #getTypes()} accepts
 * every transaction type.
 *
 * <pre>{@code
 * CurrencyHistoryQuery query = CurrencyHistoryQuery.builder().account(playerId).limit(10).build();
 * }</pre>
 */
public final class CurrencyHistoryQuery {
    private final UUID account;
    private final CurrencyActor actor;
    private final String service;
    private final Set<CurrencyTransactionType> types;
    private final Instant from;
    private final Instant until;
    private final int offset;
    private final int limit;

    /** Creates a query without filters: the newest 50 records. */
    public CurrencyHistoryQuery() {
        this(null, null, null, Collections.<CurrencyTransactionType>emptySet(), null, null, 0, 50);
    }

    /**
     * Creates a query.
     *
     * @param account match transactions involving this player as source or target
     * @param actor match the exact initiating principal
     * @param service match the exact application service identifier
     * @param types accepted transaction types, or empty for every type
     * @param from inclusive lower timestamp boundary
     * @param until exclusive upper timestamp boundary
     * @param offset number of matching records to skip
     * @param limit maximum page size, from {@code 1} through {@code 500}
     */
    public CurrencyHistoryQuery(@Nullable UUID account, @Nullable CurrencyActor actor, @Nullable String service,
                                @NotNull Set<CurrencyTransactionType> types, @Nullable Instant from,
                                @Nullable Instant until, int offset, int limit) {
        if (offset < 0) {
            throw new IllegalArgumentException("Currency history offset must not be negative");
        }
        if (limit < 1 || limit > 500) {
            throw new IllegalArgumentException("Currency history limit must be between 1 and 500");
        }
        this.account = account;
        this.actor = actor;
        this.service = service;
        this.types = Collections.unmodifiableSet(new LinkedHashSet<CurrencyTransactionType>(types));
        this.from = from;
        this.until = until;
        this.offset = offset;
        this.limit = limit;
    }

    /** Player involved as source or target, or {@code null} for any. */
    @Nullable
    public UUID getAccount() {
        return account;
    }

    /** Exact initiating principal, or {@code null} for any. */
    @Nullable
    public CurrencyActor getActor() {
        return actor;
    }

    /** Exact application service identifier, or {@code null} for any. */
    @Nullable
    public String getService() {
        return service;
    }

    /** Accepted transaction types, or empty for every type. */
    @NotNull
    public Set<CurrencyTransactionType> getTypes() {
        return types;
    }

    /** Inclusive lower timestamp boundary, or {@code null}. */
    @Nullable
    public Instant getFrom() {
        return from;
    }

    /** Exclusive upper timestamp boundary, or {@code null}. */
    @Nullable
    public Instant getUntil() {
        return until;
    }

    /** Number of matching records to skip. */
    public int getOffset() {
        return offset;
    }

    /** Maximum page size, from {@code 1} through {@code 500}. */
    public int getLimit() {
        return limit;
    }

    /** Creates a builder initialized with the defaults. */
    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    /** Creates a builder initialized with the values of this query. */
    @NotNull
    public Builder toBuilder() {
        return new Builder().account(account).actor(actor).service(service).types(types).from(from).until(until)
                .offset(offset).limit(limit);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CurrencyHistoryQuery)) {
            return false;
        }
        CurrencyHistoryQuery that = (CurrencyHistoryQuery) other;
        return offset == that.offset && limit == that.limit && Objects.equals(account, that.account)
                && Objects.equals(actor, that.actor) && Objects.equals(service, that.service)
                && types.equals(that.types) && Objects.equals(from, that.from) && Objects.equals(until, that.until);
    }

    @Override
    public int hashCode() {
        return Objects.hash(account, actor, service, types, from, until, offset, limit);
    }

    @Override
    public String toString() {
        return "CurrencyHistoryQuery(account=" + account + ", actor=" + actor + ", service=" + service
                + ", types=" + types + ", from=" + from + ", until=" + until + ", offset=" + offset
                + ", limit=" + limit + ")";
    }

    /** Mutable builder for {@link CurrencyHistoryQuery}. */
    public static final class Builder {
        private UUID account;
        private CurrencyActor actor;
        private String service;
        private Set<CurrencyTransactionType> types = Collections.emptySet();
        private Instant from;
        private Instant until;
        private int offset;
        private int limit = 50;

        Builder() {
        }

        /** Matches transactions involving this player as source or target. */
        @NotNull
        public Builder account(@Nullable UUID value) {
            account = value;
            return this;
        }

        /** Matches the exact initiating principal. */
        @NotNull
        public Builder actor(@Nullable CurrencyActor value) {
            actor = value;
            return this;
        }

        /** Matches the exact application service identifier. */
        @NotNull
        public Builder service(@Nullable String value) {
            service = value;
            return this;
        }

        /** Accepts only these transaction types; empty accepts every type. */
        @NotNull
        public Builder types(@NotNull Set<CurrencyTransactionType> value) {
            types = value;
            return this;
        }

        /** Sets the inclusive lower timestamp boundary. */
        @NotNull
        public Builder from(@Nullable Instant value) {
            from = value;
            return this;
        }

        /** Sets the exclusive upper timestamp boundary. */
        @NotNull
        public Builder until(@Nullable Instant value) {
            until = value;
            return this;
        }

        /** Sets the number of matching records to skip. */
        @NotNull
        public Builder offset(int value) {
            offset = value;
            return this;
        }

        /** Sets the maximum page size, from {@code 1} through {@code 500}. */
        @NotNull
        public Builder limit(int value) {
            limit = value;
            return this;
        }

        /** Validates and creates the immutable query. */
        @NotNull
        public CurrencyHistoryQuery build() {
            return new CurrencyHistoryQuery(account, actor, service, types, from, until, offset, limit);
        }
    }
}
