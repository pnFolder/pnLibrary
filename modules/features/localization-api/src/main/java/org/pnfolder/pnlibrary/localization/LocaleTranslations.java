package org.pnfolder.pnlibrary.localization;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayouts;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Parsed Minecraft translation table for one locale and game version.
 *
 * <p>Platform-specific reverse indexes (e.g. Bukkit materials/enchantments) are provided by platform modules on top of
 * this neutral contract.
 */
public interface LocaleTranslations {
    /** Normalized locale identifier. */
    @NotNull
    String getLocale();

    /** Provenance and integrity metadata for this table. */
    @NotNull
    TranslationMetadata getMetadata();

    /** The keyboard layouts that reverse searches over this table read queries against. */
    @NotNull
    default KeyboardLayouts getLayouts() {
        return KeyboardLayouts.builtIn;
    }

    /** Returns the translation for {@code key}, when present. */
    @NotNull
    Optional<String> translate(@NotNull String key);

    /** Returns the translation for {@code key}, or {@code key} itself when absent. */
    @NotNull
    default String translateOrKey(@NotNull String key) {
        return translate(key).orElse(key);
    }

    /**
     * Translates a typed platform {@code value} through its {@code type}.
     *
     * <p>Never returns null: when no candidate key is present the result carries a readable fallback with
     * {@link Translation#isLocalized()} set to {@code false}.
     */
    @NotNull
    default <T> Translation translate(@NotNull TranslatableType<T> type, T value) {
        List<String> keys = type.keysOf(value);
        for (String key : keys) {
            Optional<String> text = translate(key);
            if (text.isPresent()) {
                return new Translation(key, text.get(), true);
            }
        }
        return new Translation(keys.isEmpty() ? null : keys.get(0), type.fallbackName(value), false);
    }

    /** Returns an immutable key-to-text translation map. */
    @NotNull
    Map<String, String> translations();

    /** Returns a reverse-search index of raw translation keys. */
    @NotNull
    TranslationIndex<String> keys();
}
