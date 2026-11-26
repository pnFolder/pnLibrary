package org.pnfolder.pnlibrary.bukkit.currency;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletionStage;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.currency.CurrencyAccount;
import org.pnfolder.pnlibrary.api.currency.CurrencyBalanceMutation;
import org.pnfolder.pnlibrary.api.currency.CurrencyDeposits;
import org.pnfolder.pnlibrary.api.currency.CurrencyDescriptor;
import org.pnfolder.pnlibrary.api.currency.CurrencyProvider;
import org.pnfolder.pnlibrary.api.currency.CurrencyRejectReason;
import org.pnfolder.pnlibrary.api.currency.CurrencyReset;
import org.pnfolder.pnlibrary.api.currency.CurrencyResult;
import org.pnfolder.pnlibrary.api.currency.CurrencyWithdrawals;

/** Adapts supported PlayerPoints operations to the portable currency contracts. */
final class ReflectivePlayerPointsCurrency
    implements CurrencyProvider, CurrencyDeposits, CurrencyWithdrawals, CurrencyBalanceMutation, CurrencyReset {
    private final Object api;
    private final List<Method> methods;
    private final CurrencyDescriptor descriptor = new CurrencyDescriptor("Points", "", 0, RoundingMode.DOWN);

    ReflectivePlayerPointsCurrency(Object api) {
        this.api = api;
        this.methods = Arrays.asList(api.getClass().getMethods());
    }

    @NotNull
    @Override
    public CurrencyDescriptor getDescriptor() {
        return descriptor;
    }

    @NotNull
    @Override
    public CompletionStage<BigDecimal> balance(@NotNull CurrencyAccount account) {
        return CompletedStages.completed(() ->
            CompletedStages.decimal(call(Arrays.asList("lookUp", "getPoints"), account.getPlayerId())));
    }

    @NotNull
    @Override
    public CompletionStage<CurrencyResult> deposit(@NotNull CurrencyAccount account, @NotNull BigDecimal amount) {
        return mutation(Arrays.asList("give", "add"), account, amount);
    }

    @NotNull
    @Override
    public CompletionStage<CurrencyResult> withdraw(@NotNull CurrencyAccount account, @NotNull BigDecimal amount) {
        return mutation(Arrays.asList("take", "remove"), account, amount);
    }

    @NotNull
    @Override
    public CompletionStage<CurrencyResult> setBalance(@NotNull CurrencyAccount account, @NotNull BigDecimal amount) {
        return mutation(Arrays.asList("set"), account, amount);
    }

    @NotNull
    @Override
    public CompletionStage<CurrencyResult> reset(@NotNull CurrencyAccount account) {
        return CompletedStages.completed(() ->
            result(call(Arrays.asList("reset"), account.getPlayerId()), "PlayerPoints rejected reset"));
    }

    private CompletionStage<CurrencyResult> mutation(List<String> names, CurrencyAccount account, BigDecimal amount) {
        return CompletedStages.completed(() -> result(
            call(names, account.getPlayerId(), amount.intValueExact()), "PlayerPoints rejected " + names.get(0)));
    }

    private Object call(List<String> names, Object... arguments) throws ReflectiveOperationException {
        for (Method method : methods) {
            if (names.contains(method.getName()) && method.getParameterTypes().length == arguments.length) {
                return method.invoke(api, arguments);
            }
        }
        throw new IllegalStateException("PlayerPoints API " + api.getClass().getName() + " does not implement "
            + String.join("/", names));
    }

    private static CurrencyResult result(Object value, String failure) {
        if (!(value instanceof Boolean) || (Boolean) value) {
            return CurrencyResult.success();
        }
        return CurrencyResult.rejected(CurrencyRejectReason.PROVIDER_REJECTED, failure);
    }
}
