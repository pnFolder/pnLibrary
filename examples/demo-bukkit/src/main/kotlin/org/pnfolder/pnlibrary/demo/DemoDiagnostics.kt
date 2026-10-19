package org.pnfolder.pnlibrary.demo

import org.pnfolder.pnlibrary.api.currency.currencyProviders
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import java.util.function.Supplier

object DemoDiagnostics {
    fun snapshot(state: DemoState, library: PnLibrary) = Supplier<Map<String, Any?>> {
        mapOf("joins" to state.joins.get(), "online" to org.bukkit.Bukkit.getOnlinePlayers().size,
            "registeredCurrencies" to library.currencyProviders.all().size,
            "libraryVersion" to library.version.toString())
    }
}
