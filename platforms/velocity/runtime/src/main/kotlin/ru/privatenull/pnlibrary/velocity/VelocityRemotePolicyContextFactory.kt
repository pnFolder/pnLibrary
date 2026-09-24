package ru.privatenull.pnlibrary.velocity

import com.velocitypowered.api.proxy.ProxyServer
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.remote.PlatformInfo
import ru.privatenull.pnlibrary.api.remote.ProductInfo
import ru.privatenull.pnlibrary.api.remote.RemotePolicyContext
import ru.privatenull.pnlibrary.api.remote.ServerInfo
import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion

object VelocityRemotePolicyContextFactory {
    @JvmStatic fun create(plugin: Any, server: ProxyServer, values: Map<String, String> = emptyMap()): RemotePolicyContext {
        val description = server.pluginManager.fromInstance(plugin).orElseThrow().description
        val version = server.version.version
        return RemotePolicyContext.builder()
            .product(ProductInfo(description.id, description.name.orElse(description.id), description.version.orElse("unknown")))
            .platform(PlatformInfo(PlatformType.VELOCITY, server.version.name, version))
            .server(ServerInfo(version, MinecraftVersion.parseInfo(null)))
            .values(values).nativeHandle(plugin).nativeHandle(server).nativeHandle(server.pluginManager)
            .build()
    }
}
