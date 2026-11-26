package org.pnfolder.pnlibrary.currency;

import java.time.Duration;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.currency.CurrencyCommandOptions;
import org.pnfolder.pnlibrary.api.currency.CurrencyCommandSettings;
import org.pnfolder.pnlibrary.api.currency.CurrencyConfirmationMode;

/** Mutable command settings collected from the plugin's configuration callback, starting from the defaults. */
final class ManagedCommandOptions implements CurrencyCommandOptions {
    private boolean enabled;
    private String permissionPrefix;
    private String prefix;
    private String success;
    private String failure;
    private String balance;
    private String historyEmpty;
    private CurrencyConfirmationMode confirmationMode;
    private Duration confirmationTimeout;

    ManagedCommandOptions() {
        CurrencyCommandSettings defaults = new CurrencyCommandSettings();
        enabled = defaults.isEnabled();
        permissionPrefix = defaults.getPermissionPrefix();
        prefix = defaults.getPrefix();
        success = defaults.getSuccess();
        failure = defaults.getFailure();
        balance = defaults.getBalance();
        historyEmpty = defaults.getHistoryEmpty();
        confirmationMode = defaults.getConfirmationMode();
        confirmationTimeout = defaults.getConfirmationTimeout();
    }

    @Override
    @NotNull
    public CurrencyCommandOptions enabled(boolean value) {
        enabled = value;
        return this;
    }

    @Override
    @NotNull
    public CurrencyCommandOptions permissionPrefix(@NotNull String value) {
        String trimmed = value.trim();
        permissionPrefix = trimmed.isEmpty() ? null : trimmed;
        return this;
    }

    @Override
    @NotNull
    public CurrencyCommandOptions prefix(@NotNull String value) {
        prefix = value;
        return this;
    }

    @Override
    @NotNull
    public CurrencyCommandOptions success(@NotNull String value) {
        success = value;
        return this;
    }

    @Override
    @NotNull
    public CurrencyCommandOptions failure(@NotNull String value) {
        failure = value;
        return this;
    }

    @Override
    @NotNull
    public CurrencyCommandOptions balance(@NotNull String value) {
        balance = value;
        return this;
    }

    @Override
    @NotNull
    public CurrencyCommandOptions historyEmpty(@NotNull String value) {
        historyEmpty = value;
        return this;
    }

    @Override
    @NotNull
    public CurrencyCommandOptions confirmation(@NotNull CurrencyConfirmationMode mode) {
        confirmationMode = mode;
        return this;
    }

    @Override
    @NotNull
    public CurrencyCommandOptions confirmationTimeout(@NotNull Duration value) {
        confirmationTimeout = value;
        return this;
    }

    /** Creates immutable settings; validates the confirmation timeout. */
    @NotNull
    CurrencyCommandSettings build() {
        return new CurrencyCommandSettings(enabled, permissionPrefix, prefix, success, failure, balance, historyEmpty,
                confirmationMode, confirmationTimeout);
    }
}
