package org.pnfolder.pnlibrary.core.plugin

import org.pnfolder.pnlibrary.api.expansions.ExpansionManager
import org.pnfolder.pnlibrary.core.placeholders.PlaceholderHub
import org.pnfolder.pnlibrary.currency.CurrencyRuntime

/** Library-wide features that modules reach only through their own context. */
internal class SharedFeatures(
    val placeholders: PlaceholderHub,
    val currency: CurrencyRuntime,
    val expansions: ExpansionManager,
    val libraryExpansions: ExpansionManager,
)
