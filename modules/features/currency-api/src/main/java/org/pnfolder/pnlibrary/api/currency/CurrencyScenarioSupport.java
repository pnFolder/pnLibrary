package org.pnfolder.pnlibrary.api.currency;

import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext;
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult;
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext;

import java.math.BigDecimal;

/** Lookups and failure mapping shared by the scenario words {@code charge}, {@code give} and {@code balance}. */
final class CurrencyScenarioSupport {
    private CurrencyScenarioSupport() {
    }

    static Currency currencyOrNull(RequirementContext context, String reference) {
        CurrencyService service = context.service(CurrencyService.class);
        return service == null ? null : service.get(CurrencyText.strip(reference));
    }

    static BigDecimal amountOf(ActionContext context, String expression) {
        BigDecimal amount = decimalOrNull(context.text(expression));
        return amount != null && amount.signum() > 0 ? amount : null;
    }

    static BigDecimal decimalOrNull(String text) {
        try {
            return new BigDecimal(CurrencyText.strip(text));
        } catch (NumberFormatException error) {
            return null;
        }
    }

    static ActionResult.Failed failure(CurrencyResult result, Currency currency, BigDecimal amount) {
        String message = result.getMessage();
        switch (result.getStatus()) {
            case REJECTED:
                if (result.getReason() == CurrencyRejectReason.INSUFFICIENT_FUNDS) {
                    return new ActionResult.Failed("currency.insufficient-funds",
                            "Not enough money: " + currency.format(amount) + " is needed", null);
                }
                return new ActionResult.Failed("currency.rejected", message != null ? message
                        : "The currency refused the operation (" + (result.getReason() != null ? result.getReason() : "unknown") + ")", null);
            case UNSUPPORTED:
                return new ActionResult.Failed("currency.unsupported",
                        message != null ? message : "The currency does not support this operation", null);
            case UNAVAILABLE:
                return new ActionResult.Failed("currency.unavailable",
                        message != null ? message : "The currency is temporarily unavailable", null);
            default:
                return new ActionResult.Failed("currency.failed",
                        message != null ? message : "The currency operation failed", result.getError());
        }
    }
}
