package ru.privatenull.pnlibrary.velocity

import com.velocitypowered.api.proxy.ProxyServer
import ru.privatenull.pnlibrary.spi.platform.DiagnosticDetailsCollector
import ru.privatenull.pnlibrary.spi.platform.DiagnosticSections
import ru.privatenull.pnlibrary.spi.platform.PlatformSnapshot

/** Collects the small set of Velocity facts that is useful during incident diagnosis. */
internal class VelocityDiagnosticDetailsCollector(
    private val server: ProxyServer,
) {
    fun collect(snapshot: PlatformSnapshot, includeSensitive: Boolean): Map<String, Any?> =
        DiagnosticDetailsCollector { snapshot }.collect(includeSensitive) { details ->
            val plugins = server.pluginManager.plugins
            val players = server.allPlayers.toList()
            val missingDependencies = DiagnosticSections.missingDependencies(
                required = plugins.associate { container ->
                    container.description.id to container.description.dependencies
                        .filterNot { it.isOptional }
                        .map { it.id }
                },
                installed = plugins.map { it.description.id }.toSet(),
            )
            val backends = server.allServers.map { connection ->
                linkedMapOf<String, Any?>(
                    "name" to connection.serverInfo.name,
                    "players" to connection.playersConnected.size,
                ).apply {
                    if (includeSensitive) {
                        val address = connection.serverInfo.address
                        this["host"] = address.hostString
                        this["port"] = address.port
                    }
                }
            }
            val pings = players.map { it.ping }.filter { it >= 0 }

            details["missingRequiredDependencies"] = missingDependencies
            details["backends"] = backends
            details["players"] = linkedMapOf(
                "online" to players.size,
                "byBackend" to players
                    .groupingBy { it.currentServer.map { connection -> connection.serverInfo.name }.orElse("[unassigned]") }
                    .eachCount(),
                "pingMs" to DiagnosticSections.pingSummary(pings),
            )
            details["health"] = DiagnosticSections.health(
                DiagnosticSections.commonSignals(missingDependencies, pings),
            )
        }
}
