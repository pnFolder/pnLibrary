package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;

/**
 * Reads a polymorphic value from one line of text instead of a YAML object.
 *
 * <p>Declare it with {@link ConfigTextForm} on the polymorphic base type. The line {@code "[message] Hello"} can then
 * stand where the full object {@code { type: message, messages: [Hello] }} is accepted. Objects keep working.
 *
 * <p>Implementations should reject invalid text with a descriptive exception; pnLibrary adds the path.
 */
public interface ConfigTextParser<T> {
    /** Converts {@code text} into a value of the polymorphic base type. */
    @NotNull
    T parse(@NotNull String text, @NotNull ConfigSerializationContext context);
}
