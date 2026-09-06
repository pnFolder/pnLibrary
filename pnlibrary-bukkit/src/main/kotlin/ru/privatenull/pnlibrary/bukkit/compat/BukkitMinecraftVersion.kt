package ru.privatenull.pnlibrary.bukkit.compat

import org.bukkit.Bukkit
import ru.privatenull.pnlibrary.api.version.minecraft.MinecraftVersion

/** Определяет версию Minecraft через Bukkit, не смешивая Bukkit API с общим API версий. */
object BukkitMinecraftVersion {
    /** Возвращает разобранную версию текущего Bukkit/Paper/Folia-ядра. */
    @JvmStatic fun current(): MinecraftVersion = MinecraftVersion.parse(rawCurrent())

    /** Возвращает исходную строку версии Minecraft, сообщённую ядром. */
    @JvmStatic fun rawCurrent(): String = try {
        val server = Bukkit.getServer()
        val method = server.javaClass.methods.firstOrNull {
            it.name == "getMinecraftVersion" && it.parameterTypes.isEmpty()
        }
        (method?.invoke(server) as? String)?.takeIf { it.isNotBlank() }
            ?: Bukkit.getBukkitVersion().substringBefore('-')
    } catch (_: Throwable) {
        Bukkit.getBukkitVersion().substringBefore('-')
    }
}