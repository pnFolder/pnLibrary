package org.pnfolder.pnlibrary.localization

import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion

/** Loaded translation tables for one Minecraft version and one or more locales. */
interface TranslationBundle {
    /** Minecraft version represented by this bundle. */
    val version: MinecraftVersion
    /** Normalized locale identifiers available in this bundle. */
    val locales: Set<String>
    /** Returns the table identified by normalized locale [id]. */
    fun locale(id: String): LocaleTranslations
    /** Returns the table for the typed locale [id]. */
    fun locale(id: MinecraftLocale): LocaleTranslations = locale(id.code)
}
