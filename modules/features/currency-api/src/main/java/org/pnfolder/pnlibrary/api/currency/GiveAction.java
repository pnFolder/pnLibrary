package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.scenario.actions.Action;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Pays {@code amount} of {@code currency} to the player, and takes it back if a later step of the scenario fails.
 *
 * <p>After a successful payment the values {@code [paid]} and {@code [balance]} are available to later texts.
 */
public final class GiveAction implements Action {
    private final String currency;
    private final String amount;
    private final boolean undo;

    /** Creates an action with an empty currency and amount that takes the money back on failure. */
    public GiveAction() {
        this("", "", true);
    }

    /** Creates an action that takes the money back when a later step fails. */
    public GiveAction(@NotNull String currency, @NotNull String amount) {
        this(currency, amount, true);
    }

    /**
     * Creates an action.
     *
     * @param currency currency reference, as in {@link CurrencyService#get}
     * @param amount positive amount; may be a placeholder
     * @param undo whether a later failure takes the money back; {@code false} keeps it
     */
    public GiveAction(@NotNull String currency, @NotNull String amount, boolean undo) {
        this.currency = currency;
        this.amount = amount;
        this.undo = undo;
    }

    /** Currency reference. */
    @NotNull
    public String getCurrency() {
        return currency;
    }

    /** Positive amount; may be a placeholder. */
    @NotNull
    public String getAmount() {
        return amount;
    }

    /** Whether a later failure takes the money back. */
    public boolean getUndo() {
        return undo;
    }

    @NotNull
    @Override
    public ActionResult run(@NotNull ActionContext context) {
        final Currency money = CurrencyScenarioSupport.currencyOrNull(context, currency);
        if (money == null) {
            return new ActionResult.Failed("currency.unavailable", "Currency '" + currency + "' is not available", null);
        }
        final BigDecimal sum = CurrencyScenarioSupport.amountOf(context, amount);
        if (sum == null) {
            return new ActionResult.Failed("currency.invalid-amount", "'" + amount + "' is not a positive amount", null);
        }
        final UUID playerId = context.getPlayer().getUniqueId();
        CurrencyResult result = money.deposit(playerId, sum).toCompletableFuture().join();
        if (!result.isSuccess()) {
            return CurrencyScenarioSupport.failure(result, money, sum);
        }
        if (undo) {
            context.onUndo("take back " + money.format(sum), () -> {
                money.withdraw(playerId, sum).toCompletableFuture().join();
            });
        }
        context.setValue("paid", money.format(sum));
        if (result.getCurrentBalance() != null) {
            context.setValue("balance", money.format(result.getCurrentBalance()));
        }
        return ActionResult.Done.INSTANCE;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GiveAction)) {
            return false;
        }
        GiveAction that = (GiveAction) other;
        return undo == that.undo && currency.equals(that.currency) && amount.equals(that.amount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(currency, amount, undo);
    }

    @Override
    public String toString() {
        return "GiveAction(currency=" + currency + ", amount=" + amount + ", undo=" + undo + ")";
    }
}
