package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.scenario.actions.Action;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Takes {@code amount} of {@code currency} from the player, and gives it back if a later step of the scenario fails.
 *
 * <p>It fails with {@code currency.insufficient-funds} when the player cannot pay, so wrap it in {@code attempt} to
 * show a message. After a successful charge the values {@code [paid]} and {@code [balance]} are available to later
 * texts.
 *
 * <p>Money is taken through the currency service, so it blocks the calling thread until the provider answers.
 *
 * <pre>{@code
 * - type: attempt
 *   do:
 *     - { type: charge, currency: money, amount: 100 }
 *     - { type: command, command: 'give [player] diamond 1' }
 *   fail:
 *     - { type: message, messages: ['&cCould not buy: [error]'] }
 * }</pre>
 *
 * <p>The words {@code charge}, {@code give} and {@code balance} are registered by the library for every plugin.
 */
public final class ChargeAction implements Action {
    private final String currency;
    private final String amount;
    private final boolean undo;

    /** Creates an action with an empty currency and amount that undoes itself. */
    public ChargeAction() {
        this("", "", true);
    }

    /** Creates an action that undoes itself when a later step fails. */
    public ChargeAction(@NotNull String currency, @NotNull String amount) {
        this(currency, amount, true);
    }

    /**
     * Creates an action.
     *
     * @param currency currency reference, as in {@link CurrencyService#get}
     * @param amount positive amount; may be a placeholder such as {@code {shop:price}}
     * @param undo whether a later failure gives the money back; {@code false} keeps it
     */
    public ChargeAction(@NotNull String currency, @NotNull String amount, boolean undo) {
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

    /** Whether a later failure gives the money back. */
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
        CurrencyResult result = money.withdraw(playerId, sum).toCompletableFuture().join();
        if (!result.isSuccess()) {
            return CurrencyScenarioSupport.failure(result, money, sum);
        }
        if (undo) {
            context.onUndo("give back " + money.format(sum), () -> {
                money.deposit(playerId, sum).toCompletableFuture().join();
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
        if (!(other instanceof ChargeAction)) {
            return false;
        }
        ChargeAction that = (ChargeAction) other;
        return undo == that.undo && currency.equals(that.currency) && amount.equals(that.amount);
    }

    @Override
    public int hashCode() {
        return Objects.hash(currency, amount, undo);
    }

    @Override
    public String toString() {
        return "ChargeAction(currency=" + currency + ", amount=" + amount + ", undo=" + undo + ")";
    }
}
