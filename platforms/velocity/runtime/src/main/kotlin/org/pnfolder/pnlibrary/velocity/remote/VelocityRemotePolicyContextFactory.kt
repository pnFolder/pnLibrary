package org.pnfolder.pnlibrary.velocity.remote

import com.velocitypowered.api.plugin.PluginContainer
import com.velocitypowered.api.proxy.ProxyServer
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.remote.PlatformInfo
import org.pnfolder.pnlibrary.api.remote.ProductInfo
import org.pnfolder.pnlibrary.api.remote.RemotePolicyContext
import org.pnfolder.pnlibrary.api.remote.ServerInfo
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion
import kotlin.jvm.optionals.getOrDefault

/** Creates remote-policy contexts from Velocity runtime metadata. */
internal object VelocityRemotePolicyContextFactory {

    /** Builds a context for [plugin] on [server] and appends caller-defined policy [values]. */
    fun create(
        plugin: Any,
        container: PluginContainer,
        server: ProxyServer,
        values: Map<String, String> = emptyMap(),
    ): RemotePolicyContext {
        val descriptor = container.description
        val proxy = server.version

        val product = ProductInfo(
            id = descriptor.id,
            name = descriptor.name.getOrDefault(descriptor.id),
            version = descriptor.version.getOrDefault("unknown"),
        )

        val platform = PlatformInfo(
            type = PlatformType.VELOCITY,
            name = proxy.name,
            version = proxy.version,
        )

        val runtime = ServerInfo(
            version = proxy.version,
            minecraft = MinecraftVersion.parseInfo(null),
        )

        return RemotePolicyContext.builder()
            .product(product)
            .platform(platform)
            .server(runtime)
            .values(values)
            .nativeHandle(plugin)
            .nativeHandle(server)
            .nativeHandle(server.pluginManager)
            .build()
    }
}
