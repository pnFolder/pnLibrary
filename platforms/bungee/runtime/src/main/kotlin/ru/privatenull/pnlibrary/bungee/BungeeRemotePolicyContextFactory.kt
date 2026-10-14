package ru.privatenull.pnlibrary.bungee

import net.md_5.bungee.api.plugin.Plugin
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.remote.PlatformInfo
import ru.privatenull.pnlibrary.api.remote.ProductInfo
import ru.privatenull.pnlibrary.api.remote.RemotePolicyContext
import ru.privatenull.pnlibrary.api.remote.ServerInfo
import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion
import java.util.Locale

/** Creates remote-policy contexts from BungeeCord runtime metadata. */
object BungeeRemotePolicyContextFactory {
    /**
     * Builds a context for [plugin] and appends caller-defined policy [values].
     *
     * @param plugin BungeeCord plugin that owns the policy evaluation.
     * @param values Additional non-sensitive values exposed to policy rules.
     */
    @JvmStatic
    @JvmOverloads
    fun create(plugin: Plugin, values: Map<String, String> = emptyMap()): RemotePolicyContext {
        val proxy = plugin.proxy
        val description = plugin.description
        val version = proxy.version

        return RemotePolicyContext.builder()
            .product(
                ProductInfo(
                    description.name.lowercase(Locale.ROOT),
                    description.name,
                    description.version,
                ),
            )
            .platform(PlatformInfo(PlatformType.BUNGEECORD, proxy.name, version))
            .server(ServerInfo(version, MinecraftVersion.parseInfo(null)))
            .values(values)
            .nativeHandle(plugin)
            .nativeHandle(proxy)
            .nativeHandle(proxy.pluginManager)
            .build()
    }
}
