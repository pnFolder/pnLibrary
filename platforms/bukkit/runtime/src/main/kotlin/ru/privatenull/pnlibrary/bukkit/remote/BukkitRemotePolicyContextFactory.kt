package ru.privatenull.pnlibrary.bukkit.remote

import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.remote.PlatformInfo
import ru.privatenull.pnlibrary.api.remote.ProductInfo
import ru.privatenull.pnlibrary.api.remote.RemotePolicyContext
import ru.privatenull.pnlibrary.api.remote.ServerInfo
import ru.privatenull.pnlibrary.common.minecraft.MinecraftVersion
import java.util.Locale

/** Creates remote-policy contexts from Bukkit runtime metadata. */
object BukkitRemotePolicyContextFactory {
    /**
     * Builds a context for [plugin] and appends caller-defined policy [values].
     *
     * @param plugin Bukkit plugin that owns the policy evaluation.
     * @param values Additional non-sensitive values exposed to policy rules.
     */
    @JvmStatic
    @JvmOverloads
    fun create(plugin: Plugin, values: Map<String, String> = emptyMap()): RemotePolicyContext {
        val server = plugin.server
        val version = server.version

        return RemotePolicyContext.builder()
            .product(
                ProductInfo(
                    plugin.name.lowercase(Locale.ROOT),
                    plugin.name,
                    plugin.description.version,
                ),
            )
            .platform(PlatformInfo(PlatformType.BUKKIT, server.name, version))
            .server(ServerInfo(version, MinecraftVersion.parseInfo(version)))
            .values(values)
            .nativeHandle(plugin)
            .nativeHandle(server)
            .nativeHandle(server.pluginManager)
            .build()
    }
}
