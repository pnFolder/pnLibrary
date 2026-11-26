package org.pnfolder.pnlibrary.bukkit.currency;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount;
import org.pnfolder.pnlibrary.api.currency.CurrencyDeposits;
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor;
import org.pnfolder.pnlibrary.api.currency.CurrencyFormatting;
import org.pnfolder.pnlibrary.api.currency.CurrencyProvider;
import org.pnfolder.pnlibrary.api.currency.CurrencyRejectReason;
import org.pnfolder.pnlibrary.api.currency.CurrencyResult;
import org.pnfolder.pnlibrary.api.currency.CurrencyWithdrawals;

/** Adapts a Vault economy provider to the portable currency contracts. */
final class ReflectiveVaultCurrency implements CurrencyProvider, CurrencyDeposits, CurrencyWithdrawals, CurrencyFormatting {
    private final Object economy;
    private final List<Method> methods;
    private final CurrencyDescriptor descriptor;

    ReflectiveVaultCurrency(Object economy) throws InvocationTargetException {
        this.economy = economy;
        this.methods = Arrays.asList(economy.getClass().getMethods());
        Object fractional = invoke("fractionalDigits");
        this.descriptor = new CurrencyDescriptor(currencyName(), "", digits(fractional), RoundingMode.DOWN);
    }

    private String currencyName() throws InvocationTargetException {
        Object plural = invoke("currencyNamePlural");
        if (plural == null) {
            return "Money";
        }
        String name = plural.toString();
        return name.trim().isEmpty() ? "Money" : name;
    }

    private static int digits(Object fractional) {
        if (fractional == null) {
            return 2;
        }
        try {
            return Math.min(Math.max(Integer.parseInt(fractional.toString()), 0), 18);
        } catch (NumberFormatException error) {
            return 2;
        }
    }

    @NotNull
    @Override
    public CurrencyDescriptor getDescriptor() {
        return descriptor;
    }

    @NotNull
    @Override
    public CompletionStage<BigDecimal> balance(@NotNull CurrencyAccount account) {
        return CompletedStages.completed(() -> CompletedStages.decimal(invokePlayer("getBalance", account, null)));
    }

    @NotNull
    @Override
    public CompletionStage<CurrencyResult> deposit(@NotNull CurrencyAccount account, @NotNull BigDecimal amount) {
        return transaction("depositPlayer", account, amount);
    }

    @NotNull
    @Override
    public CompletionStage<CurrencyResult> withdraw(@NotNull CurrencyAccount account, @NotNull BigDecimal amount) {
        return transaction("withdrawPlayer", account, amount);
    }

    @NotNull
    @Override
    public String format(@NotNull BigDecimal amount) {
        try {
            Object formatted = invoke("format", amount.doubleValue());
            return formatted != null ? formatted.toString() : descriptor.normalize(amount).toPlainString();
        } catch (InvocationTargetException error) {
            throw new IllegalStateException(error.getCause() != null ? error.getCause() : error);
        }
    }

    private CompletionStage<CurrencyResult> transaction(String name, CurrencyAccount account, BigDecimal amount) {
        return CompletedStages.completed(() -> {
            Object response = invokePlayer(name, account, amount);
            if (response == null) {
                return CurrencyResult.unavailable("Vault returned no response");
            }
            boolean success = Boolean.TRUE.equals(callNoArgs(response, "transactionSuccess"));
            Object balance = field(response, "balance");
            Object error = field(response, "errorMessage");
            if (success) {
                return CurrencyResult.success(null, balance == null ? null : CompletedStages.decimal(balance));
            }
            return CurrencyResult.rejected(CurrencyRejectReason.PROVIDER_REJECTED, error == null ? null : error.toString());
        });
    }

    private static Object callNoArgs(Object target, String name) throws ReflectiveOperationException {
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == 0) {
                return method.invoke(target);
            }
        }
        return null;
    }

    private static Object field(Object target, String name) throws ReflectiveOperationException {
        for (java.lang.reflect.Field field : target.getClass().getFields()) {
            if (field.getName().equals(name)) {
                return field.get(target);
            }
        }
        return null;
    }

    private Object invokePlayer(String name, CurrencyAccount account, BigDecimal amount) throws ReflectiveOperationException {
        OfflinePlayer player = Bukkit.getOfflinePlayer(account.getPlayerId());
        int parameters = amount == null ? 1 : 2;
        for (Method candidate : methods) {
            if (candidate.getName().equals(name) && candidate.getParameterTypes().length == parameters
                && candidate.getParameterTypes()[0].isInstance(player)) {
                return amount == null ? candidate.invoke(economy, player) : candidate.invoke(economy, player, amount.doubleValue());
            }
        }
        throw new IllegalStateException("Vault provider " + economy.getClass().getName() + " does not implement " + name
            + "(OfflinePlayer" + (amount == null ? "" : ", double") + ")");
    }

    private Object invoke(String name, Object... arguments) throws InvocationTargetException {
        for (Method method : methods) {
            if (method.getName().equals(name) && method.getParameterTypes().length == arguments.length) {
                try {
                    return method.invoke(economy, arguments);
                } catch (IllegalAccessException error) {
                    throw new IllegalStateException(error);
                }
            }
        }
        return null;
    }
}
