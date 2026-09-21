package ru.privatenull.pnlibrary.demo

import org.bukkit.plugin.java.JavaPlugin
import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion
import ru.privatenull.pnlibrary.localization.MinecraftLocalization
import ru.privatenull.pnlibrary.localization.TranslationRequest

object DemoLocalization {
    fun start(plugin: JavaPlugin): MinecraftLocalization {
        val service = MinecraftLocalization.builder()
            .cacheDirectory(plugin.dataFolder.toPath().resolve("minecraft-translations"))
            .memoryEntries(4).downloadConcurrency(1).build()
        val version = MinecraftVersion.parse(plugin.server.version)
        if (version.known) service.load(TranslationRequest.builder().version(version)
            .locale("ru_ru").locale("en_us").fallback("en_us").build())
            .thenAccept { bundle ->
                val russian = bundle.locale("ru_ru")
                plugin.logger.info("Minecraft translations: ${russian.metadata.source}; stone matches=${russian.materials().search("камень").size}")
            }.exceptionally { error -> plugin.logger.warning("Minecraft translations unavailable: ${error.message}"); null }
        return service
    }
}
