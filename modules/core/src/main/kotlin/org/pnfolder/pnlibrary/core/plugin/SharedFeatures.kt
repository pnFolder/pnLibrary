package org.pnfolder.pnlibrary.core.plugin

import org.pnfolder.pnlibrary.api.expansions.ExpansionManager
import org.pnfolder.pnlibrary.core.placeholders.PlaceholderRuntime
import org.pnfolder.pnlibrary.currency.CurrencyRuntime

/** Library-wide features that modules reach only through their own context. */
internal class SharedFeatures(
    val placeholders: PlaceholderRuntime,
    val currency: CurrencyRuntime,
    val expansions: ExpansionManager,
    val libraryExpansions: ExpansionManager,
)
