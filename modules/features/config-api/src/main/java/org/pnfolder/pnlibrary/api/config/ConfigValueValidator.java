package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/** Performs semantic validation after successful YAML deserialization. */
@FunctionalInterface
public interface ConfigValueValidator<T> {
    /** Returns validation problems; an empty list means that the value is valid. */
    @NotNull
    List<ConfigProblem> validate(T value);
}
