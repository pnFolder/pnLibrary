package org.pnfolder.pnlibrary.demo

import org.pnfolder.pnlibrary.api.currency.Currency
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderCachePolicy
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderCacheScope
import org.pnfolder.pnlibrary.api.plugin.ModuleContext
import java.math.BigDecimal

object DemoPlaceholders {
    fun register(context: ModuleContext, currency: Currency, state: DemoState) {
        context.placeholders.register("coins", String::class.java) { placeholder ->
            placeholder
                .resolve { request -> currency.format(state.balances[request.requirePlayerId()] ?: BigDecimal.ZERO) }
                .fallback("0.00 ◈")
                .cache(PlaceholderCachePolicy(PlaceholderCacheScope.PLAYER, 2_000, 1_000))
                .publishToPlaceholderApi("pndemo", "coins")
        }
    }
}
