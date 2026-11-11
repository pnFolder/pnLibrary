package org.pnfolder.pnlibrary.velocity

import com.velocitypowered.api.proxy.ProxyServer
import org.pnfolder.pnlibrary.spi.platform.DiagnosticDetailsCollector
import org.pnfolder.pnlibrary.spi.platform.DiagnosticSections
import org.pnfolder.pnlibrary.spi.platform.PlatformSnapshot
import org.pnfolder.pnlibrary.spi.platform.ProxyBackend

/** Collects the small set of Velocity facts that is useful during incident diagnosis. */
internal class VelocityDiagnosticDetailsCollector(
    private val server: ProxyServer,
) {
    fun collect(snapshot: PlatformSnapshot, includeSensitive: Boolean): Map<String, Any?> =
        DiagnosticDetailsCollector { snapshot }.collect(includeSensitive) { details ->
            val plugins = server.pluginManager.plugins
            val players = server.allPlayers.toList()
            DiagnosticSections.proxySections(
                details = details,
                includeSensitive = includeSensitive,
                missingDependencies = DiagnosticSections.missingDependencies(
                    required = plugins.associate { container ->
                        container.description.id to container.description.dependencies
                            .filterNot { it.isOptional }
                            .map { it.id }
                    },
                    installed = plugins.map { it.description.id }.toSet(),
                ),
                backends = server.allServers.map { connection ->
                    ProxyBackend(connection.serverInfo.name, connection.playersConnected.size) {
                        connection.serverInfo.address
                    }
                },
                playerBackends = players.map { player ->
                    player.currentServer.map { connection -> connection.serverInfo.name }.orElse(null)
                },
                pings = players.map { it.ping },
            )
        }
}
