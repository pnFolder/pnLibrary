package org.pnfolder.pnlibrary.core.plugin

import org.pnfolder.pnlibrary.core.expansions.ExpansionManagerImpl
import org.pnfolder.pnlibrary.core.placeholders.PlaceholderHub
import org.pnfolder.pnlibrary.currency.CurrencyFeature

/** Library-wide features that modules reach only through their own context. */
internal class SharedFeatures(
    val placeholders: PlaceholderHub,
    val currency: CurrencyFeature,
    val expansions: ExpansionManagerImpl,
    val libraryExpansions: ExpansionManagerImpl,
)
