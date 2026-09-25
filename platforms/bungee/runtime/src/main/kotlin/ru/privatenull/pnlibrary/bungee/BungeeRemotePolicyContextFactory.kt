package ru.privatenull.pnlibrary.bungee

import net.md_5.bungee.api.plugin.Plugin
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.remote.PlatformInfo
import ru.privatenull.pnlibrary.api.remote.ProductInfo
import ru.privatenull.pnlibrary.api.remote.RemotePolicyContext
import ru.privatenull.pnlibrary.api.remote.ServerInfo
import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion

object BungeeRemotePolicyContextFactory {
    @JvmStatic fun create(plugin: Plugin, values: Map<String, String> = emptyMap()): RemotePolicyContext {
        val version = plugin.proxy.version
        return RemotePolicyContext.builder()
            .product(ProductInfo(plugin.description.name.lowercase(java.util.Locale.ROOT), plugin.description.name, plugin.description.version))
            .platform(PlatformInfo(PlatformType.BUNGEECORD, plugin.proxy.name, version))
            .server(ServerInfo(version, MinecraftVersion.parseInfo(null)))
            .values(values).nativeHandle(plugin).nativeHandle(plugin.proxy).nativeHandle(plugin.proxy.pluginManager)
            .build()
    }
}
