package org.pnfolder.pnlibrary.config;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.config.ConfigScope;
import org.pnfolder.pnlibrary.api.config.ConfigurationService;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;

/** Runtime integration contract for creating and releasing module-owned configuration scopes. */
public interface ConfigurationRuntime extends ConfigurationService, AutoCloseable {
    /** Returns the scope of {@code owner} registered under {@code pluginId} as module {@code main}, creating it on first use. */
    @NotNull
    ConfigScope scope(@NotNull Object owner, @NotNull PluginId pluginId);

    /**
     * Returns the scope of {@code owner} kept under {@code key} for {@code module} of {@code plugin}, creating it on first use.
     * The scope's files are listed as {@code plugin/module:path}.
     */
    @NotNull
    ConfigScope scope(@NotNull Object owner, @NotNull PluginId key, @NotNull PluginId plugin, @NotNull ModuleId module);

    /** Closes every scope created for {@code owner}; call it when the owner is disabled. */
    void close(@NotNull Object owner);

    @Override
    void close();
}
