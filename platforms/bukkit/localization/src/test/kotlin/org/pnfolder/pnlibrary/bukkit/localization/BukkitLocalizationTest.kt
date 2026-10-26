package org.pnfolder.pnlibrary.bukkit.localization

import org.bukkit.Material
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion
import org.pnfolder.pnlibrary.localization.LocaleTranslations
import org.pnfolder.pnlibrary.localization.MinecraftLocale
import org.pnfolder.pnlibrary.localization.MinecraftLocalization
import org.pnfolder.pnlibrary.localization.TranslationBundle
import org.pnfolder.pnlibrary.localization.TranslationMetadata
import org.pnfolder.pnlibrary.localization.TranslationRequest
import org.pnfolder.pnlibrary.localization.TranslationSource
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayouts
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage

class BukkitLocalizationTest {
    private val table = object : LocaleTranslations {
        override val locale = "ru_ru"
        override val metadata = TranslationMetadata(TranslationSource.MEMORY, null, false)
        override val layouts = KeyboardLayouts.builtIn
        override fun translate(key: String): Optional<String> = Optional.ofNullable(mapOf("item.minecraft.diamond" to "Алмаз")[key])
        override fun translations() = mapOf("item.minecraft.diamond" to "Алмаз")
        override fun keys() = error("not needed")
    }

    private val loaded = mutableListOf<String>()

    private val localization = object : MinecraftLocalization {
        override val layouts = KeyboardLayouts.builtIn
        override fun availableVersions(): CompletionStage<List<MinecraftVersion>> = error("not needed")
        override fun availableLocales(version: MinecraftVersion): CompletionStage<List<String>> = error("not needed")
        override fun load(request: TranslationRequest): CompletionStage<TranslationBundle> = error("not needed")
        override fun refresh(request: TranslationRequest): CompletionStage<TranslationBundle> = error("not needed")
        override fun locale(version: MinecraftVersion, locale: String): CompletionStage<LocaleTranslations> {
            loaded += locale
            return CompletableFuture.completedFuture(table)
        }
        override fun close() = Unit
    }

    @Test
    fun `a loaded table already translates Bukkit values`() {
        val ru = localization.bukkitLocale(MinecraftVersion.V1_20_5, MinecraftLocale.RU_RU).toCompletableFuture().join()

        assertEquals("Алмаз", ru.translate(Material.DIAMOND).text)
        assertEquals(listOf("ru_ru"), loaded)
    }

    @Test
    fun `a locale code string loads the same table`() {
        val ru = BukkitLocalization.locale(localization, MinecraftVersion.V1_20_5, "ru_ru").toCompletableFuture().join()

        assertEquals("Алмаз", ru.translate(Material.DIAMOND).text)
    }
}
