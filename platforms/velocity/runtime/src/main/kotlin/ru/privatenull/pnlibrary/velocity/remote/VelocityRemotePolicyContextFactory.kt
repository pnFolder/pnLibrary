package ru.privatenull.pnlibrary.velocity.remote

import com.velocitypowered.api.plugin.PluginContainer
import com.velocitypowered.api.proxy.ProxyServer
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.remote.PlatformInfo
import ru.privatenull.pnlibrary.api.remote.ProductInfo
import ru.privatenull.pnlibrary.api.remote.RemotePolicyContext
import ru.privatenull.pnlibrary.api.remote.ServerInfo
import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion

/** Creates remote-policy contexts from Velocity runtime metadata. */
internal object VelocityRemotePolicyContextFactory {

    /**
     * Builds a context for [plugin] on [server] and appends caller-defined policy [values].
     *
     * @param plugin Native Velocity plugin instance that owns the policy evaluation.
     * @param container Velocity container registered for [plugin].
     * @param server Velocity proxy that hosts the plugin.
     * @param values Additional non-sensitive values exposed to policy rules.
     */
    fun create(
        plugin: Any,
        container: PluginContainer,
        server: ProxyServer,
        values: Map<String, String> = emptyMap(),
    ): RemotePolicyContext {
        val manager = server.pluginManager
        val descriptor = container.description

        val proxy = server.version

        val product = ProductInfo(
            descriptor.id,
            descriptor.name.orElse(descriptor.id),
            descriptor.version.orElse("unknown"),
        )

        val platform = PlatformInfo(
            PlatformType.VELOCITY,
            proxy.name,
            proxy.version,
        )

        val runtime = ServerInfo(
            proxy.version,
            MinecraftVersion.parseInfo(null),
        )

        return RemotePolicyContext.builder()
            .product(product)
            .platform(platform)
            .server(runtime)
            .values(values)
            .nativeHandle(plugin)
            .nativeHandle(server)
            .nativeHandle(manager)
            .build()
    }
}
