package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Objects;

/**
 * Immutable command presentation and destructive-operation confirmation settings.
 *
 * <p>Message strings may contain legacy {@code &} or {@code §} color codes and documented brace tokens such as
 * {@code {currency}}, {@code {amount}}, {@code {balance}}, and {@code {error}}. The defaults use the legacy colors of
 * pnLibrary's chat palette. Exact command availability and rendering remain platform-specific.
 */
public final class CurrencyCommandSettings {
    private static final Duration MIN_CONFIRMATION_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration MAX_CONFIRMATION_TIMEOUT = Duration.ofMinutes(10);

    private final boolean enabled;
    private final String permissionPrefix;
    private final String prefix;
    private final String success;
    private final String failure;
    private final String balance;
    private final String historyEmpty;
    private final CurrencyConfirmationMode confirmationMode;
    private final Duration confirmationTimeout;

    /** Creates the default settings. */
    public CurrencyCommandSettings() {
        this(builder());
    }

    private CurrencyCommandSettings(Builder b) {
        this(b.enabled, b.permissionPrefix, b.prefix, b.success, b.failure, b.balance, b.historyEmpty,
                b.confirmationMode, b.confirmationTimeout);
    }

    /**
     * Creates settings.
     *
     * @param enabled whether platform command integration is exposed
     * @param permissionPrefix custom permission root, or {@code null} for the generated default
     * @param prefix component text prepended to command responses
     * @param success template for successful mutations
     * @param failure template for failed or rejected operations
     * @param balance template for balance lookup
     * @param historyEmpty response used when a ledger query returns no rows
     * @param confirmationMode second-step policy for destructive administration
     * @param confirmationTimeout confirmation lifetime from 10 seconds through 10 minutes
     */
    public CurrencyCommandSettings(boolean enabled, @Nullable String permissionPrefix, @NotNull String prefix,
                                   @NotNull String success, @NotNull String failure, @NotNull String balance,
                                   @NotNull String historyEmpty, @NotNull CurrencyConfirmationMode confirmationMode,
                                   @NotNull Duration confirmationTimeout) {
        if (confirmationTimeout.compareTo(MIN_CONFIRMATION_TIMEOUT) < 0
                || confirmationTimeout.compareTo(MAX_CONFIRMATION_TIMEOUT) > 0) {
            throw new IllegalArgumentException("Currency confirmation timeout must be between 10 seconds and 10 minutes");
        }
        this.enabled = enabled;
        this.permissionPrefix = permissionPrefix;
        this.prefix = prefix;
        this.success = success;
        this.failure = failure;
        this.balance = balance;
        this.historyEmpty = historyEmpty;
        this.confirmationMode = confirmationMode;
        this.confirmationTimeout = confirmationTimeout;
    }

    /** Whether platform command integration is exposed. */
    public boolean isEnabled() {
        return enabled;
    }

    /** Custom permission root, or {@code null} for the generated default. */
    @Nullable
    public String getPermissionPrefix() {
        return permissionPrefix;
    }

    /** Component text prepended to command responses. */
    @NotNull
    public String getPrefix() {
        return prefix;
    }

    /** Template for successful mutations. */
    @NotNull
    public String getSuccess() {
        return success;
    }

    /** Template for failed or rejected operations. */
    @NotNull
    public String getFailure() {
        return failure;
    }

    /** Template for balance lookup. */
    @NotNull
    public String getBalance() {
        return balance;
    }

    /** Response used when a ledger query returns no rows. */
    @NotNull
    public String getHistoryEmpty() {
        return historyEmpty;
    }

    /** Second-step policy for destructive administration. */
    @NotNull
    public CurrencyConfirmationMode getConfirmationMode() {
        return confirmationMode;
    }

    /** Confirmation lifetime from 10 seconds through 10 minutes. */
    @NotNull
    public Duration getConfirmationTimeout() {
        return confirmationTimeout;
    }

    /** Creates a builder initialized with the defaults. */
    @NotNull
    public static Builder builder() {
        return new Builder();
    }

    /** Creates a builder initialized with the values of these settings. */
    @NotNull
    public Builder toBuilder() {
        return new Builder().enabled(enabled).permissionPrefix(permissionPrefix).prefix(prefix).success(success)
                .failure(failure).balance(balance).historyEmpty(historyEmpty).confirmationMode(confirmationMode)
                .confirmationTimeout(confirmationTimeout);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof CurrencyCommandSettings)) {
            return false;
        }
        CurrencyCommandSettings that = (CurrencyCommandSettings) other;
        return enabled == that.enabled && Objects.equals(permissionPrefix, that.permissionPrefix)
                && prefix.equals(that.prefix) && success.equals(that.success) && failure.equals(that.failure)
                && balance.equals(that.balance) && historyEmpty.equals(that.historyEmpty)
                && confirmationMode == that.confirmationMode && confirmationTimeout.equals(that.confirmationTimeout);
    }

    @Override
    public int hashCode() {
        return Objects.hash(enabled, permissionPrefix, prefix, success, failure, balance, historyEmpty,
                confirmationMode, confirmationTimeout);
    }

    @Override
    public String toString() {
        return "CurrencyCommandSettings(enabled=" + enabled + ", permissionPrefix=" + permissionPrefix
                + ", prefix=" + prefix + ", success=" + success + ", failure=" + failure + ", balance=" + balance
                + ", historyEmpty=" + historyEmpty + ", confirmationMode=" + confirmationMode
                + ", confirmationTimeout=" + confirmationTimeout + ")";
    }

    /** Mutable builder for {@link CurrencyCommandSettings}, initialized with the defaults. */
    public static final class Builder {
        private boolean enabled = true;
        private String permissionPrefix;
        private String prefix = "&7[&d{currency}&7] ";
        private String success = "&aОперация выполнена: &f{amount}";
        private String failure = "&c{error}";
        private String balance = "&bБаланс: &f{balance}";
        private String historyEmpty = "&bОпераций пока нет.";
        private CurrencyConfirmationMode confirmationMode = CurrencyConfirmationMode.CONSOLE;
        private Duration confirmationTimeout = Duration.ofSeconds(60);

        Builder() {
        }

        /** Sets whether platform command integration is exposed. */
        @NotNull
        public Builder enabled(boolean value) {
            enabled = value;
            return this;
        }

        /** Sets the custom permission root; {@code null} keeps the generated default. */
        @NotNull
        public Builder permissionPrefix(@Nullable String value) {
            permissionPrefix = value;
            return this;
        }

        /** Sets the component text prepended to command responses. */
        @NotNull
        public Builder prefix(@NotNull String value) {
            prefix = value;
            return this;
        }

        /** Sets the template for successful mutations. */
        @NotNull
        public Builder success(@NotNull String value) {
            success = value;
            return this;
        }

        /** Sets the template for failed or rejected operations. */
        @NotNull
        public Builder failure(@NotNull String value) {
            failure = value;
            return this;
        }

        /** Sets the template for balance lookup. */
        @NotNull
        public Builder balance(@NotNull String value) {
            balance = value;
            return this;
        }

        /** Sets the response used when a ledger query returns no rows. */
        @NotNull
        public Builder historyEmpty(@NotNull String value) {
            historyEmpty = value;
            return this;
        }

        /** Sets the second-step policy for destructive administration. */
        @NotNull
        public Builder confirmationMode(@NotNull CurrencyConfirmationMode value) {
            confirmationMode = value;
            return this;
        }

        /** Sets the confirmation lifetime from 10 seconds through 10 minutes. */
        @NotNull
        public Builder confirmationTimeout(@NotNull Duration value) {
            confirmationTimeout = value;
            return this;
        }

        /** Validates and creates the immutable settings. */
        @NotNull
        public CurrencyCommandSettings build() {
            return new CurrencyCommandSettings(this);
        }
    }
}
