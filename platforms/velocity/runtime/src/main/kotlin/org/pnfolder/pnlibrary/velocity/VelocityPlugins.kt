package org.pnfolder.pnlibrary.velocity

import com.velocitypowered.api.plugin.PluginContainer
import com.velocitypowered.api.proxy.ProxyServer
import org.pnfolder.pnlibrary.spi.platform.PlatformPlugins
import org.pnfolder.pnlibrary.spi.platform.PluginSnapshot

/** Velocity plugins, resolved from their instances and described by their plugin descriptions. */
internal fun velocityPlugins(proxy: ProxyServer): PlatformPlugins<PluginContainer> = PlatformPlugins(
    platformName = "Velocity",
    installed = { proxy.pluginManager.plugins },
    resolve = { owner -> proxy.pluginManager.fromInstance(owner).orElse(null) },
) { container ->
    val description = container.description
    PluginSnapshot(
        id = description.id,
        name = description.name.orElse(description.id),
        version = description.version.orElse("unknown"),
        authors = description.authors,
    )
}
