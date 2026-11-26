package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Operations available inside one {@code version(n, changes -> ...)} block. */
public final class ConfigChanges {
    private final ConfigDocument document;

    ConfigChanges(ConfigDocument document) {
        this.document = document;
    }

    /** Returns the value at {@code path}. The path must exist. */
    @NotNull
    public Value get(@NotNull String path) {
        if (!document.contains(path)) {
            throw new IllegalArgumentException("No value at '" + path + "'");
        }
        return new Value(document.get(path));
    }

    /** Writes {@code value} at {@code path}, creating missing parent sections. Accepts {@link Value} results directly. */
    public void put(@NotNull String path, @Nullable Object value) {
        document.set(path, value instanceof Value ? ((Value) value).unwrap() : value);
    }

    /** Removes the key at {@code path}. Missing keys are ignored. */
    public void remove(@NotNull String path) {
        if (document.contains(path)) {
            document.remove(path);
        }
    }

    /** Moves the value from {@code from} to {@code to}, removing the old key. The source must exist. */
    public void rename(@NotNull String from, @NotNull String to) {
        document.move(from, to);
    }
}
