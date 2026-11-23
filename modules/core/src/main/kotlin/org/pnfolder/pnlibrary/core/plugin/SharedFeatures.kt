package org.pnfolder.pnlibrary.core.plugin

import org.pnfolder.pnlibrary.api.expansions.ExpansionManager
import org.pnfolder.pnlibrary.core.placeholders.PlaceholderEngine
import org.pnfolder.pnlibrary.currency.CurrencyRuntime

/** Library-wide features shared by all modules: placeholders, currency and expansions. */
internal class SharedFeatures(
    val placeholders: PlaceholderEngine,
    val currency: CurrencyRuntime,
    val expansions: ExpansionManager,
    val libraryExpansions: ExpansionManager,
)
