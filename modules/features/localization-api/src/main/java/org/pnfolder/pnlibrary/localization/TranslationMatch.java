package org.pnfolder.pnlibrary.localization;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/** One deterministic reverse-lookup result. The value is {@code null} for keys unknown to the local API. */
public final class TranslationMatch<T> {
    private final String key;
    private final String translation;
    private final T value;

    /** Creates a result for {@code key}, its localized text and the local API object it represents. */
    public TranslationMatch(@NotNull String key, @NotNull String translation, @Nullable T value) {
        this.key = key;
        this.translation = translation;
        this.value = value;
    }

    /** Canonical Minecraft translation key. */
    @NotNull
    public String getKey() {
        return key;
    }

    /** Localized text matched by the query. */
    @NotNull
    public String getTranslation() {
        return translation;
    }

    /** Local API object represented by the key, when known. */
    @Nullable
    public T getValue() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TranslationMatch)) {
            return false;
        }
        TranslationMatch<?> that = (TranslationMatch<?>) other;
        return key.equals(that.key) && translation.equals(that.translation) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, translation, value);
    }

    @Override
    public String toString() {
        return "TranslationMatch(key=" + key + ", translation=" + translation + ", value=" + value + ")";
    }
}
