package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;

/** Reads one nested configuration value; supplied by the configuration runtime. */
@FunctionalInterface
public interface ConfigValueDecoder {
    /** Converts {@code value}, found at {@code path}, into {@code type}. */
    @Nullable
    Object decode(@Nullable Object value, @NotNull Type type, @NotNull String path);
}
