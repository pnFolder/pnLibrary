package org.pnfolder.pnlibrary.bungee

import net.md_5.bungee.api.plugin.Plugin
import org.pnfolder.pnlibrary.spi.platform.DiagnosticDetailsCollector
import org.pnfolder.pnlibrary.spi.platform.DiagnosticSections
import org.pnfolder.pnlibrary.spi.platform.PlatformSnapshot
import org.pnfolder.pnlibrary.spi.platform.ProxyBackend

/** Collects the small set of BungeeCord facts that is useful during incident diagnosis. */
internal class BungeeDiagnosticDetailsCollector(
    private val plugin: Plugin,
) {
    fun collect(snapshot: PlatformSnapshot, includeSensitive: Boolean): Map<String, Any?> =
        DiagnosticDetailsCollector { snapshot }.collect(includeSensitive) { details ->
            val proxy = plugin.proxy
            val plugins = proxy.pluginManager.plugins
            val players = proxy.players.toList()
            DiagnosticSections.proxySections(
                details = details,
                includeSensitive = includeSensitive,
                missingDependencies = DiagnosticSections.missingDependencies(
                    required = plugins.associate { it.description.name to it.description.depends },
                    installed = plugins.map { it.description.name }.toSet(),
                ),
                backends = proxy.servers.map { (name, info) ->
                    // ServerInfo.socketAddress is missing on old proxies; address stays supported there.
                    @Suppress("DEPRECATION")
                    ProxyBackend(name, info.players.size) { info.address }
                },
                playerBackends = players.map { it.server?.info?.name },
                pings = players.map { it.ping.toLong() },
            )
        }
}
