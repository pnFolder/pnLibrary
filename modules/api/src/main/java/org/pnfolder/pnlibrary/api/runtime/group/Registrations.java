package org.pnfolder.pnlibrary.api.runtime.group;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.expansions.ExpansionManager;
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapterRegistry;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistry;
import org.pnfolder.pnlibrary.api.services.ServiceManager;

/**
 * What plugins register with the library: themselves, services, shared features and placeholder bridges.
 *
 * <pre>{@code
 * PnLibraryProvider.get().getRegistry().getPlugins().register(this);
 * }</pre>
 */
@ApiStatus.NonExtendable
public interface Registrations {
    /** Global registry and high-level entry point for consumer plugins. */
    @NotNull
    PluginRegistry getPlugins();

    /** Typed process-wide services with plugin-owned registrations. */
    @NotNull
    ServiceManager getServices();

    /** Shared features, such as localization, that the library enables and disables as units. */
    @NotNull
    ExpansionManager getExpansions();

    /** Runtime-detected placeholder bridges such as PlaceholderAPI; plugins normally use their plugin context instead. */
    @NotNull
    PlaceholderAdapterRegistry getPlaceholderAdapters();
}
