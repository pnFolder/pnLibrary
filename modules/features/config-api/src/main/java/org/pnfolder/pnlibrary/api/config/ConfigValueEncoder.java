package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Type;

/** Writes one nested configuration value; supplied by the configuration runtime. */
@FunctionalInterface
public interface ConfigValueEncoder {
    /** Converts {@code value}, declared as {@code type} at {@code path}, into YAML-compatible data. */
    @Nullable
    Object encode(@Nullable Object value, @NotNull Type type, @NotNull String path);
}
