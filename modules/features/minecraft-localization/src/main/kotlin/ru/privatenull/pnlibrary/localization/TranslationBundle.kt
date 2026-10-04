package ru.privatenull.pnlibrary.localization

import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion

/** Loaded translation tables for one Minecraft version and one or more locales. */
interface TranslationBundle {
    /** Minecraft version represented by this bundle. */
    val version: MinecraftVersion
    /** Normalized locale identifiers available in this bundle. */
    val locales: Set<String>
    /** Returns the table identified by normalized locale [id]. */
    fun locale(id: String): LocaleTranslations
}
