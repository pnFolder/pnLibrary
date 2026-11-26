package org.pnfolder.pnlibrary.core.plugin.module;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.plugin.metadata.PluginMetadataBuilder;

/** The name, version, authors and description a module sets in its builder, written into the definition. */
final class ModuleMetadataDefinition implements PluginMetadataBuilder {
    private final ModuleDefinitionBuilder definition;

    ModuleMetadataDefinition(ModuleDefinitionBuilder definition) {
        this.definition = definition;
    }

    @Override
    public @NotNull PluginMetadataBuilder name(@NotNull String value) {
        definition.setMetadataName(require(value, "name"));
        return this;
    }

    @Override
    public @NotNull PluginMetadataBuilder version(@NotNull String value) {
        definition.setMetadataVersion(require(value, "version"));
        return this;
    }

    @Override
    public @NotNull PluginMetadataBuilder description(@NotNull String value) {
        definition.setMetadataDescription(require(value, "description"));
        return this;
    }

    @Override
    public @NotNull PluginMetadataBuilder authors(@NotNull String value) {
        definition.setMetadataAuthors(require(value, "authors"));
        return this;
    }

    private static String require(String value, String field) {
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Plugin metadata " + field + " must not be blank");
        }
        return trimmed;
    }
}
