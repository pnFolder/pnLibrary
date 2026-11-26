package org.pnfolder.pnlibrary.config.yaml;

import java.util.Set;

import org.jetbrains.annotations.NotNull;

/** Optional schema metadata understood by the managed YAML lifecycle. */
public interface ConfigSchema {
    /** Dotted paths of the fields marked {@code @ConfigRequired}; they must be present in an existing file. */
    @NotNull
    Set<String> getRequiredPaths();
}
