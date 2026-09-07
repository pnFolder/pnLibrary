package ru.privatenull.pnlibrary.bukkit.compat

import org.bukkit.Bukkit
import ru.privatenull.pnlibrary.api.version.minecraft.MinecraftVersion

/** Resolves Minecraft versions through Bukkit without leaking Bukkit into the shared version API. */
object BukkitMinecraftVersion {
    /** Returns the parsed version of the current Bukkit/Paper/Folia server. */
    @JvmStatic fun current(): MinecraftVersion = MinecraftVersion.parse(rawCurrent())

    /** Returns the raw Minecraft version reported by the server. */
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
