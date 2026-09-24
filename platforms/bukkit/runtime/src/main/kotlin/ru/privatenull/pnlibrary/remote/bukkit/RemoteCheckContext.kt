package ru.privatenull.pnlibrary.remote.bukkit

import org.bukkit.plugin.java.JavaPlugin
import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion
import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersionInfo
import java.util.Collections
import java.util.Locale

class RemoteCheckContext internal constructor(
    val plugin: JavaPlugin,
    values: Map<String, String>,
) {
    val pluginVersion: String = plugin.description.version
    val serverVersion: String = plugin.server.version
    val minecraftVersionInfo: MinecraftVersionInfo = MinecraftVersion.parseInfo(serverVersion)
    val minecraftVersion: MinecraftVersion = minecraftVersionInfo.parsed
    val serverInfo: BukkitServerInfo = BukkitServerInfo(
        detectPlatform(plugin.server.name), plugin.server.name, serverVersion, minecraftVersionInfo,
    )
    val values: Map<String, String> = Collections.unmodifiableMap(HashMap(values))

    fun plugin() = plugin
    fun pluginVersion() = pluginVersion
    fun serverVersion() = serverVersion
    fun minecraftVersion() = minecraftVersion
    fun minecraftVersionInfo() = minecraftVersionInfo
    fun serverInfo() = serverInfo
    fun values() = values

    private fun detectPlatform(name: String): BukkitPlatform {
        val normalized = name.lowercase(Locale.ROOT)
        return when {
            "purpur" in normalized -> BukkitPlatform.PURPUR
            "folia" in normalized -> BukkitPlatform.FOLIA
            "paper" in normalized -> BukkitPlatform.PAPER
            "spigot" in normalized -> BukkitPlatform.SPIGOT
            "craftbukkit" in normalized -> BukkitPlatform.CRAFTBUKKIT
            "bukkit" in normalized -> BukkitPlatform.BUKKIT
            else -> BukkitPlatform.UNKNOWN
        }
    }
}
