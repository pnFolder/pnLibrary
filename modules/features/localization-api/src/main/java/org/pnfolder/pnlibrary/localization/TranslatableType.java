package org.pnfolder.pnlibrary.localization;

import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A typed category of platform values (materials, enchantments, biomes, ...) that resolve to Minecraft
 * translation keys.
 *
 * <p>Platform modules implement one per value type so any typed object translates through the same
 * {@link LocaleTranslations#translate(TranslatableType, Object)} entry point, without adding a method per type to the
 * contract.
 */
public interface TranslatableType<T> {
    /** Candidate translation keys for {@code value}, in priority order; the first key present in the table wins. */
    @NotNull
    List<String> keysOf(T value);

    /** Last-resort human-readable name used when none of {@link #keysOf} is present in the table. */
    @NotNull
    String fallbackName(T value);
}
