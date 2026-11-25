package org.pnfolder.pnlibrary.api.runtime.group

import org.jetbrains.annotations.ApiStatus

import org.pnfolder.pnlibrary.api.expansions.ExpansionManager
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapterRegistry
import org.pnfolder.pnlibrary.api.plugin.PluginRegistry
import org.pnfolder.pnlibrary.api.services.ServiceManager

/**
 * What plugins register with the library: themselves, services, shared features and placeholder bridges.
 *
 * ```kotlin
 * PnLibraryProvider.get().registry.plugins.register(this)
 * ```
 */
@ApiStatus.NonExtendable
interface Registrations {
    /** Global registry and high-level entry point for consumer plugins. */
    val plugins: PluginRegistry

    /** Typed process-wide services with plugin-owned registrations. */
    val services: ServiceManager

    /** Shared features, such as localization, that the library enables and disables as units. */
    val expansions: ExpansionManager

    /** Runtime-detected placeholder bridges such as PlaceholderAPI; plugins normally use their plugin context instead. */
    val placeholderAdapters: PlaceholderAdapterRegistry
}
