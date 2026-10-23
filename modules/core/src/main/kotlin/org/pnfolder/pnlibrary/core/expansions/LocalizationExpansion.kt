package org.pnfolder.pnlibrary.core.expansions

import org.pnfolder.pnlibrary.api.expansions.AbstractExpansion
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion
import org.pnfolder.pnlibrary.localization.MinecraftLocalization
import org.pnfolder.pnlibrary.localization.TranslationException
import org.pnfolder.pnlibrary.localization.TranslationRequest
import org.pnfolder.pnlibrary.localization.layout.KeyboardLayouts
import java.nio.file.Path

/**
 * One translation loader shared by every module, which reach it only through [bind]. Nothing is downloaded when it
 * starts: modules request the versions and locales they need and the loader caches them by version and locale.
 * Keyboard layouts outlive disable/enable cycles and are dropped when the expansion closes.
 */
internal class LocalizationExpansion(
    private val cacheDirectory: Path,
) : AbstractExpansion("localization"), ModuleBoundExpansion<MinecraftLocalization> {
    @Volatile
    private var active: MinecraftLocalization? = null
    private val layoutRegistry = KeyboardLayouts.create()

    /** Follows enable/disable cycles of the loader; closing it does nothing. */
    private val shared: MinecraftLocalization = object : MinecraftLocalization {
        override val layouts get() = layoutRegistry
        override fun availableVersions() = current().availableVersions()
        override fun availableLocales(version: MinecraftVersion) = current().availableLocales(version)
        override fun load(request: TranslationRequest) = current().load(request)
        override fun refresh(request: TranslationRequest) = current().refresh(request)
        override fun close() = Unit
    }

    override fun bind(module: ModuleBinding): MinecraftLocalization = ModuleLocalization(shared, module)

    override fun onEnable() {
        active = MinecraftLocalization.builder()
            .cacheDirectory(cacheDirectory)
            .memoryEntries(MEMORY_ENTRIES)
            .layouts(layoutRegistry)
            .build()
    }

    override fun onDisable() {
        active?.close()
        active = null
    }

    override fun onClose() = layoutRegistry.close()

    private fun current() = active
        ?: throw TranslationException(TranslationException.Reason.CLOSED, "Minecraft localization is disabled")

    private companion object {
        /** The default of 8 bundles is sized for a single plugin; this cache serves all of them across many locales. */
        const val MEMORY_ENTRIES = 32
    }
}
