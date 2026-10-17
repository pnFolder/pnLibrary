package ru.privatenull.pnlibrary.bungee

import net.md_5.bungee.api.plugin.Plugin
import ru.privatenull.pnlibrary.spi.platform.DiagnosticDetailsCollector
import ru.privatenull.pnlibrary.spi.platform.DiagnosticSections
import ru.privatenull.pnlibrary.spi.platform.PlatformSnapshot

/** Collects the small set of BungeeCord facts that is useful during incident diagnosis. */
internal class BungeeDiagnosticDetailsCollector(
    private val plugin: Plugin,
) {
    fun collect(snapshot: PlatformSnapshot, includeSensitive: Boolean): Map<String, Any?> =
        DiagnosticDetailsCollector { snapshot }.collect(includeSensitive) { details ->
            val proxy = plugin.proxy
            val plugins = proxy.pluginManager.plugins
            val missingDependencies = DiagnosticSections.missingDependencies(
                required = plugins.associate { it.description.name to it.description.depends },
                installed = plugins.map { it.description.name }.toSet(),
            )
            val backends = proxy.servers.map { (name, info) ->
                linkedMapOf<String, Any?>(
                    "name" to name,
                    "players" to info.players.size,
                ).apply {
                    if (includeSensitive) {
                        this["host"] = info.address.hostString
                        this["port"] = info.address.port
                    }
                }
            }
            val pings = proxy.players.map { it.ping.toLong() }.filter { it >= 0 }

            details["missingRequiredDependencies"] = missingDependencies
            details["backends"] = backends
            details["players"] = linkedMapOf(
                "online" to proxy.onlineCount,
                "byBackend" to proxy.players
                    .groupingBy { it.server?.info?.name ?: "[unassigned]" }
                    .eachCount(),
                "pingMs" to DiagnosticSections.pingSummary(pings),
            )
            details["health"] = DiagnosticSections.health(
                DiagnosticSections.commonSignals(missingDependencies, pings),
            )
        }
}
