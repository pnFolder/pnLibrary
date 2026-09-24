package ru.privatenull.pnlibrary.remote.bukkit

import org.bukkit.plugin.java.JavaPlugin
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.remote.PlatformInfo
import ru.privatenull.pnlibrary.api.remote.ProductInfo
import ru.privatenull.pnlibrary.api.remote.RemotePolicyContext
import ru.privatenull.pnlibrary.api.remote.ServerInfo
import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion

object BukkitRemotePolicyContextFactory {
    @JvmStatic fun create(plugin: JavaPlugin, values: Map<String, String> = emptyMap()): RemotePolicyContext {
        val version = plugin.server.version
        return RemotePolicyContext.builder()
            .product(ProductInfo(plugin.name.lowercase(), plugin.name, plugin.description.version))
            .platform(PlatformInfo(PlatformType.BUKKIT, plugin.server.name, version))
            .server(ServerInfo(version, MinecraftVersion.parseInfo(version)))
            .values(values).nativeHandle(plugin).nativeHandle(plugin.server).nativeHandle(plugin.server.pluginManager)
            .build()
    }
}
