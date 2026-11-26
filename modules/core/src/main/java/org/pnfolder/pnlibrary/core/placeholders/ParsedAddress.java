package org.pnfolder.pnlibrary.core.placeholders;

import java.util.Collections;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** An address taken apart: the words of the path and the formats after it. */
public final class ParsedAddress {
    private final List<String> words;
    private final List<FormatCall> formats;
    private final String owner;

    public ParsedAddress(@NotNull List<String> words, @NotNull List<FormatCall> formats, @Nullable String owner) {
        this.words = Collections.unmodifiableList(words);
        this.formats = Collections.unmodifiableList(formats);
        this.owner = owner;
    }

    public @NotNull List<String> getWords() {
        return words;
    }

    public @NotNull List<FormatCall> getFormats() {
        return formats;
    }

    public @Nullable String getOwner() {
        return owner;
    }
}
