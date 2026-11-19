package org.pnfolder.pnlibrary.api.currency

import org.pnfolder.pnlibrary.api.scenario.actions.Action
import org.pnfolder.pnlibrary.api.scenario.actions.ActionContext
import org.pnfolder.pnlibrary.api.scenario.actions.ActionResult
import org.pnfolder.pnlibrary.api.scenario.requirements.Requirement
import org.pnfolder.pnlibrary.api.scenario.requirements.RequirementContext
import org.pnfolder.pnlibrary.api.scenario.requirements.Verdict
import java.math.BigDecimal

private fun RequirementContext.currencyOrNull(reference: String): Currency? =
    service(CurrencyService::class.java)?.get(reference.trim())

private fun ActionContext.amountOf(expression: String): BigDecimal? =
    text(expression).trim().toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

private fun CurrencyResult.failure(currency: Currency, amount: BigDecimal): ActionResult.Failed = when (status) {
    CurrencyResultStatus.REJECTED -> when (reason) {
        CurrencyRejectReason.INSUFFICIENT_FUNDS -> ActionResult.Failed(
            "currency.insufficient-funds",
            "Not enough money: ${currency.format(amount)} is needed",
        )
        else -> ActionResult.Failed("currency.rejected", message ?: "The currency refused the operation (${reason ?: "unknown"})")
    }
    CurrencyResultStatus.UNSUPPORTED -> ActionResult.Failed("currency.unsupported", message ?: "The currency does not support this operation")
    CurrencyResultStatus.UNAVAILABLE -> ActionResult.Failed("currency.unavailable", message ?: "The currency is temporarily unavailable")
    else -> ActionResult.Failed("currency.failed", message ?: "The currency operation failed", error)
}

/**
 * Takes [amount] of [currency] from the player, and gives it back if a later step of the scenario fails.
 *
 * It fails with `currency.insufficient-funds` when the player cannot pay, so wrap it in `attempt` to show a
 * message. After a successful charge the values `[paid]` and `[balance]` are available to later texts.
 *
 * Money is taken through the currency service, so it blocks the calling thread until the provider answers.
 *
 * ```yaml
 * - type: attempt
 *   do:
 *     - { type: charge, currency: money, amount: 100 }
 *     - { type: command, command: 'give [player] diamond 1' }
 *   fail:
 *     - { type: message, messages: ['&cCould not buy: [error]'] }
 * ```
 *
 * The words `charge`, `give` and `balance` are registered by the library for every plugin.
 *
 * @property currency currency reference, as in [CurrencyService.get]
 * @property amount positive amount; may be a placeholder such as `{shop:price}`
 * @property undo whether a later failure gives the money back; `false` keeps it
 */
data class ChargeAction(val currency: String = "", val amount: String = "", val undo: Boolean = true) : Action {
    override fun run(context: ActionContext): ActionResult {
        val money = context.currencyOrNull(currency)
            ?: return ActionResult.Failed("currency.unavailable", "Currency '$currency' is not available")
        val sum = context.amountOf(amount)
            ?: return ActionResult.Failed("currency.invalid-amount", "'$amount' is not a positive amount")
        val result = money.withdraw(context.player.uniqueId, sum).toCompletableFuture().join()
        if (!result.isSuccess) return result.failure(money, sum)
        if (undo) context.onUndo("give back ${money.format(sum)}") { money.deposit(context.player.uniqueId, sum).toCompletableFuture().join() }
        context.setValue("paid", money.format(sum))
        result.currentBalance?.let { context.setValue("balance", money.format(it)) }
        return ActionResult.Done
    }
}

/**
 * Pays [amount] of [currency] to the player, and takes it back if a later step of the scenario fails.
 *
 * After a successful payment the values `[paid]` and `[balance]` are available to later texts.
 *
 * @property currency currency reference, as in [CurrencyService.get]
 * @property amount positive amount; may be a placeholder
 * @property undo whether a later failure gives the money back; `false` keeps it
 */
data class GiveAction(val currency: String = "", val amount: String = "", val undo: Boolean = true) : Action {
    override fun run(context: ActionContext): ActionResult {
        val money = context.currencyOrNull(currency)
            ?: return ActionResult.Failed("currency.unavailable", "Currency '$currency' is not available")
        val sum = context.amountOf(amount)
            ?: return ActionResult.Failed("currency.invalid-amount", "'$amount' is not a positive amount")
        val result = money.deposit(context.player.uniqueId, sum).toCompletableFuture().join()
        if (!result.isSuccess) return result.failure(money, sum)
        if (undo) context.onUndo("take back ${money.format(sum)}") { money.withdraw(context.player.uniqueId, sum).toCompletableFuture().join() }
        context.setValue("paid", money.format(sum))
        result.currentBalance?.let { context.setValue("balance", money.format(it)) }
        return ActionResult.Done
    }
}

/**
 * Met when the player has at least [atLeast] of [currency]. It only looks: to take the money use `charge`,
 * which cannot disagree with this check.
 *
 * @property currency currency reference, as in [CurrencyService.get]
 * @property atLeast the least balance; may be a placeholder
 */
data class BalanceRequirement(val currency: String = "", val atLeast: String = "") : Requirement {
    override fun test(context: RequirementContext): Verdict {
        val money = context.currencyOrNull(currency) ?: return Verdict.Unmet("currency '$currency' is not available")
        val need = context.text(atLeast).trim().toBigDecimalOrNull() ?: return Verdict.Unmet("'$atLeast' is not an amount")
        val has = money.has(context.player.uniqueId, need).toCompletableFuture().join()
        return Verdict.of(has, "need ${money.format(need)}")
    }
}

