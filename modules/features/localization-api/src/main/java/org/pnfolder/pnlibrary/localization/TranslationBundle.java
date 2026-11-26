package org.pnfolder.pnlibrary.localization;

import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion;

import java.util.Set;

/** Loaded translation tables for one Minecraft version and one or more locales. */
public interface TranslationBundle {
    /** Minecraft version represented by this bundle. */
    @NotNull
    MinecraftVersion getVersion();

    /** Normalized locale identifiers available in this bundle. */
    @NotNull
    Set<String> getLocales();

    /** Returns the table identified by normalized locale {@code id}. */
    @NotNull
    LocaleTranslations locale(@NotNull String id);

    /** Returns the table for the typed locale {@code id}. */
    @NotNull
    default LocaleTranslations locale(@NotNull MinecraftLocale id) {
        return locale(id.getCode());
    }
}
