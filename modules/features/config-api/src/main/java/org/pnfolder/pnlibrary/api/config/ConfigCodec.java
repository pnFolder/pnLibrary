package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;

/** Converts between a typed code-first model {@code T} and YAML text. */
public interface ConfigCodec<T> {
    /** Serializes {@code value}, including default fields and documentation comments. */
    @NotNull
    String encode(T value);

    /** Deserializes YAML. Errors should identify the invalid field path when possible. */
    T decode(@NotNull String yaml);
}
