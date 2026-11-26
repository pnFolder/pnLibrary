package org.pnfolder.pnlibrary.core.plugin.module;

import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.metadata.PluginMetadata;
import org.pnfolder.pnlibrary.common.jvm.JavaRuntime;
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity;
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership;

/** Builds module metadata from the definition, falling back to the native plugin descriptor. */
public final class ModuleMetadataFactory {
    private final PlatformOwnership ownership;
    private final PlatformIdentity identity;

    public ModuleMetadataFactory(@NotNull PlatformOwnership ownership, @NotNull PlatformIdentity identity) {
        this.ownership = ownership;
        this.identity = identity;
    }

    public @NotNull PluginMetadata create(@NotNull Object owner, @NotNull ModuleId moduleId, @NotNull ModuleDefinitionBuilder definition) {
        Map<String, String> nativeMetadata = ownership.ownerDetails(owner);
        String description = definition.getMetadataDescription();
        if (description == null) {
            description = notBlank(nativeMetadata.get("description"));
        }
        return new PluginMetadata(
                moduleId,
                orNative(definition.getMetadataName(), nativeMetadata, "name", moduleId.getValue()),
                orNative(definition.getMetadataVersion(), nativeMetadata, "version", "unknown"),
                orNative(definition.getMetadataAuthors(), nativeMetadata, "authors", "unknown"),
                identity.getType(),
                identity.getImplementationName(),
                System.getProperty("java.version", "unknown"),
                JavaRuntime.getFeature(),
                description);
    }

    private static String orNative(@Nullable String value, Map<String, String> nativeMetadata, String key, String fallback) {
        if (value != null) {
            return value;
        }
        String fromNative = notBlank(nativeMetadata.get(key));
        return fromNative != null ? fromNative : fallback;
    }

    private static @Nullable String notBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty() ? null : value;
    }
}
