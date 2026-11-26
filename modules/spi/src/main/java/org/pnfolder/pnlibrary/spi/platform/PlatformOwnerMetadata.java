package org.pnfolder.pnlibrary.spi.platform;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Logger;

/** Metadata and compatibility checks for a native plugin owner. */
public interface PlatformOwnerMetadata {
    /** Metadata of the native plugin {@code owner}, or {@code null} when it is not a known plugin. */
    @Nullable
    default PluginSnapshot ownerMetadata(@NotNull Object owner) {
        return null;
    }

    /** Plugin id, name, version and authors of {@code owner} as strings; empty when unknown. */
    @NotNull
    default Map<String, String> ownerDetails(@NotNull Object owner) {
        PluginSnapshot metadata = ownerMetadata(owner);
        if (metadata == null) {
            return Collections.emptyMap();
        }
        Map<String, String> details = new LinkedHashMap<>();
        details.put("id", metadata.getId());
        details.put("name", metadata.getName());
        details.put("version", metadata.getVersion());
        details.put("authors", String.join(", ", metadata.getAuthors()));
        return details;
    }

    /** Whether {@code owner} is a plugin this platform recognises. */
    default boolean acceptsOwner(@NotNull Object owner) {
        return !ownerDetails(owner).isEmpty();
    }

    /** The folder where {@code owner} keeps its files, or {@code null} when this platform gives plugins none (Velocity). */
    @Nullable
    default Path ownerDataFolder(@NotNull Object owner) {
        return null;
    }

    /** The logger of {@code owner}, or {@code null} when this platform gives plugins none. */
    @Nullable
    default Logger ownerLogger(@NotNull Object owner) {
        return null;
    }
}
