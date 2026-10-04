package ru.privatenull.pnlibrary.velocity

import com.velocitypowered.api.proxy.ProxyServer
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.remote.PlatformInfo
import ru.privatenull.pnlibrary.api.remote.ProductInfo
import ru.privatenull.pnlibrary.api.remote.RemotePolicyContext
import ru.privatenull.pnlibrary.api.remote.ServerInfo
import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion

/** Creates remote-policy contexts from Velocity runtime metadata. */
object VelocityRemotePolicyContextFactory {
    /**
     * Builds a context for [plugin] on [server] and appends caller-defined policy [values].
     *
     * @param plugin Native Velocity plugin instance that owns the policy evaluation.
     * @param server Velocity proxy that hosts the plugin.
     * @param values Additional non-sensitive values exposed to policy rules.
     */
    @JvmStatic
    @JvmOverloads
    fun create(plugin: Any, server: ProxyServer, values: Map<String, String> = emptyMap()): RemotePolicyContext {
        val description = server.pluginManager.fromInstance(plugin).orElseThrow().description
        val version = server.version.version
        return RemotePolicyContext.builder()
            .product(
                ProductInfo(
                    description.id,
                    description.name.orElse(description.id),
                    description.version.orElse("unknown"))
            )
            .platform(
                PlatformInfo(
                    PlatformType.VELOCITY,
                    server.version.name,
                    version)
            )
            .server(
                ServerInfo(
                    version,
                    MinecraftVersion.parseInfo(null))
            )
            .values(values)
            .nativeHandle(plugin).nativeHandle(server).nativeHandle(server.pluginManager)
            .build()
    }
}
