package org.pnfolder.pnlibrary.updates.engine.catalog;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Invalid release-catalog field with its logical field path. */
public final class ManifestException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    private final String field;

    public ManifestException(@NotNull String field, @NotNull String message) {
        this(field, message, null);
    }

    public ManifestException(@NotNull String field, @NotNull String message, @Nullable Throwable cause) {
        super("Invalid release catalog field '" + field + "': " + message, cause);
        this.field = field;
    }

    /** Logical JSON field path that failed validation. */
    @NotNull
    public String getField() {
        return field;
    }
}
