package org.pnfolder.pnlibrary.bukkit.localization

import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion
import org.pnfolder.pnlibrary.localization.MinecraftLocale
import org.pnfolder.pnlibrary.localization.MinecraftLocalization
import java.util.concurrent.CompletionStage

/** Loads translations that already speak Bukkit types, so callers do not wrap the neutral table themselves. */
object BukkitLocalization {
    /** Loads [locale] for [version] from [localization] and wraps it with Bukkit lookups. */
    @JvmStatic
    fun locale(localization: MinecraftLocalization, version: MinecraftVersion, locale: String): CompletionStage<BukkitLocaleTranslations> =
        localization.locale(version, locale).thenApply(BukkitLocaleTranslations::wrap)

    /** Loads the typed [locale] for [version] from [localization] and wraps it with Bukkit lookups. */
    @JvmStatic
    fun locale(localization: MinecraftLocalization, version: MinecraftVersion, locale: MinecraftLocale): CompletionStage<BukkitLocaleTranslations> =
        locale(localization, version, locale.code)
}

/** Loads [locale] for [version] with Bukkit lookups; shorthand for [BukkitLocalization.locale]. */
fun MinecraftLocalization.bukkitLocale(version: MinecraftVersion, locale: MinecraftLocale): CompletionStage<BukkitLocaleTranslations> =
    BukkitLocalization.locale(this, version, locale)

/** Loads the locale with code [locale] for [version] with Bukkit lookups. */
fun MinecraftLocalization.bukkitLocale(version: MinecraftVersion, locale: String): CompletionStage<BukkitLocaleTranslations> =
    BukkitLocalization.locale(this, version, locale)
