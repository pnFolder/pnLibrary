package ru.privatenull.pnlibrary.demo

import ru.privatenull.pnlibrary.api.currency.Currency
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderCachePolicy
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderCacheScope
import ru.privatenull.pnlibrary.api.plugin.ModuleContext
import java.math.BigDecimal

object DemoPlaceholders {
    fun register(context: ModuleContext, currency: Currency, state: DemoState) {
        context.placeholders.placeholder("coins", String::class.java)
            .resolve { request -> currency.format(state.balances[request.requirePlayerId()] ?: BigDecimal.ZERO) }
            .fallback("0.00 ◈").cache(PlaceholderCachePolicy(PlaceholderCacheScope.PLAYER, 2_000, 1_000))
            .publishToPlaceholderApi("pndemo", "coins").register()
    }
}
