package ru.privatenull.pnlibrary.localization

import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion

interface TranslationBundle {
    val version: MinecraftVersion
    val locales: Set<String>
    fun locale(id: String): LocaleTranslations
}
