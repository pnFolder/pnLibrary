package org.pnfolder.pnlibrary.core.placeholders;

import java.util.Objects;
import org.jetbrains.annotations.NotNull;

/** One way to write an address: a short label, the text to put into a message and what it means. */
public final class UsageVariant {
    private final String label;
    private final String text;
    private final String meaning;

    public UsageVariant(@NotNull String label, @NotNull String text, @NotNull String meaning) {
        this.label = Objects.requireNonNull(label, "label");
        this.text = Objects.requireNonNull(text, "text");
        this.meaning = Objects.requireNonNull(meaning, "meaning");
    }

    public @NotNull String getLabel() {
        return label;
    }

    public @NotNull String getText() {
        return text;
    }

    public @NotNull String getMeaning() {
        return meaning;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof UsageVariant)) {
            return false;
        }
        UsageVariant that = (UsageVariant) other;
        return label.equals(that.label) && text.equals(that.text) && meaning.equals(that.meaning);
    }

    @Override
    public int hashCode() {
        return Objects.hash(label, text, meaning);
    }

    @Override
    public String toString() {
        return "UsageVariant(label=" + label + ", text=" + text + ", meaning=" + meaning + ")";
    }
}
