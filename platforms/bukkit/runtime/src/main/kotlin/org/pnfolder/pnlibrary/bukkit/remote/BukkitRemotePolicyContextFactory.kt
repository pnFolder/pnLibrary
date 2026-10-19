package org.pnfolder.pnlibrary.bukkit.remote

import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.remote.PlatformInfo
import org.pnfolder.pnlibrary.api.remote.ProductInfo
import org.pnfolder.pnlibrary.api.remote.RemotePolicyContext
import org.pnfolder.pnlibrary.api.remote.ServerInfo
import org.pnfolder.pnlibrary.common.minecraft.MinecraftVersion
import java.util.Locale

/** Creates remote-policy contexts from Bukkit runtime metadata. */
internal object BukkitRemotePolicyContextFactory {
    /**
     * Builds a context for [plugin] and appends caller-defined policy [values].
     *
     * @param plugin Bukkit plugin that owns the policy evaluation.
     * @param values Additional non-sensitive values exposed to policy rules.
     */
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
