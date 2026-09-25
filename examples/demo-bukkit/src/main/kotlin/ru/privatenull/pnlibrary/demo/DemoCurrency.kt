package ru.privatenull.pnlibrary.demo

import ru.privatenull.pnlibrary.api.currency.Currency
import ru.privatenull.pnlibrary.api.currency.CurrencyRejectReason
import ru.privatenull.pnlibrary.api.currency.CurrencyResult
import ru.privatenull.pnlibrary.api.currency.currencies
import ru.privatenull.pnlibrary.api.plugin.ModuleContext
import java.math.BigDecimal
import java.math.RoundingMode

object DemoCurrency {
    fun register(context: ModuleContext, state: DemoState): Currency = context.currencies.register("coins") { definition ->
        definition.descriptor { it.displayName("Demo Coins").symbol("◈").fractionDigits(2).roundingMode(RoundingMode.DOWN) }
        definition.operations { operations ->
            operations.balance { account -> state.balances[account.playerId] ?: zero() }
                .deposit { account, amount -> mutate(state, account.playerId, amount, true) }
                .withdraw { account, amount -> mutate(state, account.playerId, amount, false) }
                .setBalance { account, amount ->
                    val value = amount.setScale(2, RoundingMode.DOWN); state.balances[account.playerId] = value
                    CurrencyResult.success(null, value)
                }
                .reset { account -> state.balances.remove(account.playerId); CurrencyResult.success() }
                .format { amount -> "${amount.setScale(2, RoundingMode.DOWN)} ◈" }
        }
    }

    private fun mutate(state: DemoState, id: java.util.UUID, amount: BigDecimal, add: Boolean): CurrencyResult {
        val old = state.balances[id] ?: zero(); val next = if (add) old + amount else old - amount
        if (next < BigDecimal.ZERO) return CurrencyResult.rejected(CurrencyRejectReason.INSUFFICIENT_FUNDS)
        val normalized = next.setScale(2, RoundingMode.DOWN); state.balances[id] = normalized
        return CurrencyResult.success(old, normalized)
    }
    private fun zero() = BigDecimal.ZERO.setScale(2)
}
