package org.pnfolder.pnlibrary.core.plugin.module;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor;
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateRequest;
import org.pnfolder.pnlibrary.api.version.PnLibraryApi;
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership;

/** Resolves the product descriptor of a module from its definition or the native plugin. */
public final class ModuleProductResolver {
    private final PlatformOwnership platform;

    public ModuleProductResolver(@NotNull PlatformOwnership platform) {
        this.platform = platform;
    }

    public @Nullable ProductDescriptor resolve(@NotNull Object owner, @NotNull ModuleId moduleId,
                                               @NotNull ModuleDefinitionBuilder definition) {
        ProductDescriptor explicit = definition.getProductDescriptor() == null
                ? null : bindIfNecessary(definition.getProductDescriptor(), moduleId);
        PluginUpdateRequest request = definition.getUpdateRequest();
        ProductDescriptor inferred = null;
        if (request != null) {
            inferred = ProductDescriptor.builder()
                    .version(resolveVersion(owner, moduleId, definition))
                    .pnLibraryApi(request.getSupportedApi().getMinimum(), request.getSupportedApi().getMaximum())
                    .build();
        } else if (explicit == null && !definition.getDependencies().isEmpty()) {
            inferred = ProductDescriptor.builder()
                    .version(resolveVersion(owner, moduleId, definition))
                    .pnLibraryApi(PnLibraryApi.VERSION, PnLibraryApi.VERSION)
                    .build();
        }
        ProductDescriptor chosen = explicit != null ? explicit : inferred;
        if (chosen == null) {
            return null;
        }
        ProductDescriptor bound = bindIfNecessary(chosen, moduleId);
        if (!bound.getId().getValue().equals(moduleId.getValue())) {
            throw new IllegalArgumentException("Component ID " + bound.getId() + " does not match module ID " + moduleId);
        }
        return bound;
    }

    private String resolveVersion(Object owner, ModuleId moduleId, ModuleDefinitionBuilder definition) {
        String fromPlatform = platform.ownerDetails(owner).get("version");
        if (fromPlatform != null) {
            return fromPlatform;
        }
        if (definition.getMetadataVersion() != null) {
            return definition.getMetadataVersion();
        }
        throw new IllegalStateException("Cannot infer component version for " + moduleId);
    }

    private static ProductDescriptor bindIfNecessary(ProductDescriptor descriptor, ModuleId moduleId) {
        return descriptor.isBound() ? descriptor : descriptor.bindTo(moduleId.getValue());
    }
}
