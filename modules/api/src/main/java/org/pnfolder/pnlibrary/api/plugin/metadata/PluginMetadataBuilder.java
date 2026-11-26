package org.pnfolder.pnlibrary.api.plugin.metadata;

import org.jetbrains.annotations.NotNull;

/**
 * Optional display metadata overrides used during {@code PluginRegistry.register}.
 *
 * <p>Omitted values are discovered through the platform adapter. These methods do not change the normalized
 * {@code PluginId} used by registries and cross-plugin access rules.
 */
public interface PluginMetadataBuilder {
    /** Overrides the human-readable plugin name. */
    @NotNull
    PluginMetadataBuilder name(@NotNull String value);

    /** Overrides the plugin version shown in lifecycle and diagnostic output. */
    @NotNull
    PluginMetadataBuilder version(@NotNull String value);

    /** Says in a sentence or two what the plugin or module is for; {@code /pn modules} and the menu show it. */
    @NotNull
    default PluginMetadataBuilder description(@NotNull String value) {
        return this;
    }

    /** Overrides the display-ready author text. */
    @NotNull
    PluginMetadataBuilder authors(@NotNull String value);
}
