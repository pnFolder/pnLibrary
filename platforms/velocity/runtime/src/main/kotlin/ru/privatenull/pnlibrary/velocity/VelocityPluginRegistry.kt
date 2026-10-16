package ru.privatenull.pnlibrary.velocity

import com.velocitypowered.api.plugin.PluginContainer
import com.velocitypowered.api.plugin.PluginDescription
import com.velocitypowered.api.proxy.ProxyServer
import ru.privatenull.pnlibrary.spi.platform.PluginSnapshot

/** Resolves native plugin instances and converts their metadata in one place. */
internal class VelocityPluginRegistry(private val proxy: ProxyServer) {
    fun find(owner: Any): PluginContainer? =
        proxy.pluginManager.fromInstance(owner).orElse(null)

    fun require(owner: Any): PluginContainer = requireNotNull(find(owner)) {
        "Owner must be a registered Velocity plugin; received ${owner.javaClass.name}"
    }

    fun snapshot(owner: Any): PluginSnapshot? = find(owner)?.description?.toSnapshot()

    fun snapshots(): List<PluginSnapshot> =
        proxy.pluginManager.plugins.map { it.description.toSnapshot() }

    fun versions(): Map<String, String> = snapshots().associate { it.id to it.version }
}

/** The same conversion is used by owner lookup and support snapshots. */
private fun PluginDescription.toSnapshot(): PluginSnapshot = PluginSnapshot(
    id = id,
    name = name.orElse(id),
    version = version.orElse("unknown"),
    authors = authors,
)
