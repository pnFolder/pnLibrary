package org.pnfolder.pnlibrary.localization;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Result of translating a typed platform value.
 *
 * <p>{@link #isLocalized()} is {@code true} when {@link #getText()} came from a translation table entry, and
 * {@code false} when no candidate key matched and the text is a readable fallback derived from the value itself.
 * {@link #getKey()} is the translation key that produced the text, or the first candidate key when the result is a
 * fallback.
 */
public final class Translation {
    private final String key;
    private final String text;
    private final boolean localized;

    /** Creates a result; {@code key} is {@code null} when the value had no candidate keys. */
    public Translation(@Nullable String key, @NotNull String text, boolean localized) {
        this.key = key;
        this.text = text;
        this.localized = localized;
    }

    /** Translation key that produced the text, or the first candidate key for a fallback. */
    @Nullable
    public String getKey() {
        return key;
    }

    /** Translated or fallback text. */
    @NotNull
    public String getText() {
        return text;
    }

    /** Whether the text came from a translation table entry. */
    public boolean isLocalized() {
        return localized;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Translation)) {
            return false;
        }
        Translation that = (Translation) other;
        return localized == that.localized && Objects.equals(key, that.key) && text.equals(that.text);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, text, localized);
    }

    @Override
    public String toString() {
        return "Translation(key=" + key + ", text=" + text + ", localized=" + localized + ")";
    }
}
