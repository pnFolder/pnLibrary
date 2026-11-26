package org.pnfolder.pnlibrary.api.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** A setting could not be changed; the message says why and is meant to be shown to the person who typed the value. */
public class ConfigEditException extends IllegalArgumentException {
    private static final long serialVersionUID = 1L;

    /** Creates a failure with a message for the person who typed the value. */
    public ConfigEditException(@NotNull String message) {
        super(message);
    }

    /** Creates a failure that wraps its {@code cause}. */
    public ConfigEditException(@NotNull String message, @Nullable Throwable cause) {
        super(message, cause);
    }
}
