package org.pnfolder.pnlibrary.api.currency;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement;
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext;
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Met when the player has at least {@code atLeast} of {@code currency}. It only looks: to take the money use
 * {@code charge}, which cannot disagree with this check.
 */
public final class BalanceRequirement implements Requirement {
    private final String currency;
    private final String atLeast;

    /** Creates a requirement with an empty currency and amount. */
    public BalanceRequirement() {
        this("", "");
    }

    /**
     * Creates a requirement.
     *
     * @param currency currency reference, as in {@link CurrencyService#get}
     * @param atLeast the least balance; may be a placeholder
     */
    public BalanceRequirement(@NotNull String currency, @NotNull String atLeast) {
        this.currency = Objects.requireNonNull(currency, "currency");
        this.atLeast = Objects.requireNonNull(atLeast, "atLeast");
    }

    /** Currency reference. */
    @NotNull
    public String getCurrency() {
        return currency;
    }

    /** The least balance; may be a placeholder. */
    @NotNull
    public String getAtLeast() {
        return atLeast;
    }

    @NotNull
    @Override
    public Verdict test(@NotNull RequirementContext context) {
        Currency money = CurrencyScenarioSupport.currencyOrNull(context, currency);
        if (money == null) {
            return new Verdict.Unmet("currency '" + currency + "' is not available");
        }
        BigDecimal need = CurrencyScenarioSupport.decimalOrNull(context.text(atLeast));
        if (need == null) {
            return new Verdict.Unmet("'" + atLeast + "' is not an amount");
        }
        boolean has = money.has(context.getPlayer().getUniqueId(), need).toCompletableFuture().join();
        return Verdict.of(has, "need " + money.format(need));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof BalanceRequirement && currency.equals(((BalanceRequirement) other).currency)
                && atLeast.equals(((BalanceRequirement) other).atLeast);
    }

    @Override
    public int hashCode() {
        return Objects.hash(currency, atLeast);
    }

    @Override
    public String toString() {
        return "BalanceRequirement(currency=" + currency + ", atLeast=" + atLeast + ")";
    }
}
