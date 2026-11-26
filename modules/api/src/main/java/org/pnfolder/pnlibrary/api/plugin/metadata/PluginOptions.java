package org.pnfolder.pnlibrary.api.plugin.metadata;

import org.jetbrains.annotations.NotNull;

/** Optional switches that change how pnLibrary integrates one registered plugin. */
public interface PluginOptions {
    /**
     * Controls publication of eligible placeholders through the detected PlaceholderAPI adapter.
     *
     * <p>This option is enabled by default. Disabling it does not disable pnLibrary's own placeholder resolution
     * through {@code ModuleContext.placeholders}.
     */
    @NotNull
    PluginOptions placeholderApi(boolean enabled);
}
