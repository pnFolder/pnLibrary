package org.pnfolder.pnlibrary.core.placeholders.address;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** A piece of an address that holds inner placeholders: plain text, or the text of one inner placeholder. */
public final class AddressSegment {
    private final boolean inner;
    private final String text;

    public AddressSegment(boolean inner, @NotNull String text) {
        this.inner = inner;
        this.text = Objects.requireNonNull(text, "text");
    }

    public boolean isInner() {
        return inner;
    }

    public @NotNull String getText() {
        return text;
    }
}
