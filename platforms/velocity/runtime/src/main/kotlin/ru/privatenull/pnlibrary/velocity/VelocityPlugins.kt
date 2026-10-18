package ru.privatenull.pnlibrary.velocity

import com.velocitypowered.api.plugin.PluginContainer
import com.velocitypowered.api.proxy.ProxyServer
import ru.privatenull.pnlibrary.spi.platform.PlatformPlugins
import ru.privatenull.pnlibrary.spi.platform.PluginSnapshot

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
