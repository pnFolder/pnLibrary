package org.pnfolder.pnlibrary.core.plugin.module.definition;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.plugin.metadata.PluginOptions;

/** The options a module sets in its builder, written into the definition. */
final class ModuleOptionsDefinition implements PluginOptions {
    private final ModuleDefinitionBuilder definition;

    ModuleOptionsDefinition(ModuleDefinitionBuilder definition) {
        this.definition = definition;
    }

    @Override
    public @NotNull PluginOptions placeholderApi(boolean enabled) {
        definition.setPlaceholderApiEnabled(enabled);
        return this;
    }
}
