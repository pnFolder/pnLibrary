package org.pnfolder.pnlibrary.api.placeholders.reply;

import org.jetbrains.annotations.Nullable;

/** Used inside the library to end an address with an answer; plugins use {@link Reply#stop}. */
public final class PlaceholderAnswer extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final transient Object value;

    /** Ends the address with {@code value}. */
    public PlaceholderAnswer(@Nullable Object value) {
        super(null, null, false, false);
        this.value = value;
    }

    /** The answer. */
    @Nullable
    public Object getValue() {
        return value;
    }
}
