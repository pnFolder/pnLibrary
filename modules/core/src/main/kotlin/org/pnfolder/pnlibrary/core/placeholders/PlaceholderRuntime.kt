package org.pnfolder.pnlibrary.core.placeholders

import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapterRegistry
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderService
import org.pnfolder.pnlibrary.api.plugin.PluginId

/** Shared adapters and factory for isolated module-owned placeholder registrations. */
internal interface PlaceholderRuntime : PlaceholderAdapterRegistry {
    fun scope(owner: PluginId, placeholderApiEnabled: Boolean = true): PlaceholderService
}
