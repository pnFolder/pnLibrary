package ru.privatenull.pnlibrary.demo

import ru.privatenull.pnlibrary.api.currency.currencyProviders
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import java.util.function.Supplier

object DemoDiagnostics {
    fun snapshot(state: DemoState, library: PnLibrary) = Supplier<Map<String, Any?>> {
        mapOf("joins" to state.joins.get(), "online" to org.bukkit.Bukkit.getOnlinePlayers().size,
            "registeredCurrencies" to library.currencyProviders.all().size,
            "libraryVersion" to library.version.toString())
    }
}
