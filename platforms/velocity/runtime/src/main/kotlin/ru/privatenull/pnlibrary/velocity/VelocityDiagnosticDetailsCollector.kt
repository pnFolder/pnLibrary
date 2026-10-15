package ru.privatenull.pnlibrary.velocity

import com.velocitypowered.api.proxy.ProxyServer
import ru.privatenull.pnlibrary.spi.platform.DiagnosticDetailsCollector
import ru.privatenull.pnlibrary.spi.platform.PlatformSnapshot

/** Collects the small set of Velocity facts that is useful during incident diagnosis. */
internal class VelocityDiagnosticDetailsCollector(
    private val server: ProxyServer,
) {
    fun collect(snapshot: PlatformSnapshot, includeSensitive: Boolean): Map<String, Any?> =
        DiagnosticDetailsCollector { snapshot }.collect(includeSensitive) { details ->
            val plugins = server.pluginManager.plugins
            val installedIds = plugins.map { it.description.id }.toSet()
            val missingDependencies = plugins.mapNotNull { container ->
                val missing = container.description.dependencies
                    .filterNot { it.isOptional }
                    .map { it.id }
                    .filterNot(installedIds::contains)
                missing.takeIf { it.isNotEmpty() }?.let {
                    linkedMapOf("plugin" to container.description.id, "missing" to it)
                }
            }
            val backends = server.allServers.map { connection ->
                val address = connection.serverInfo.address
                linkedMapOf<String, Any?>(
                    "name" to connection.serverInfo.name,
                    "players" to connection.playersConnected.size,
                ).apply {
                    if (includeSensitive) {
                        this["host"] = address.hostString
                        this["port"] = address.port
                    }
                }
            }
            val pings = server.allPlayers.map { it.ping }.filter { it >= 0 }
            val p95 = percentile(pings, 0.95)
            val signals = buildList<Map<String, Any?>> {
                if (missingDependencies.isNotEmpty()) {
                    add(linkedMapOf("code" to "missingRequiredDependencies", "count" to missingDependencies.size))
                }
                if (p95 != null && p95 >= HIGH_PING_THRESHOLD_MS) {
                    add(linkedMapOf("code" to "highPlayerLatency", "p95Ms" to p95))
                }
            }

            details["missingRequiredDependencies"] = missingDependencies
            details["backends"] = backends
            details["players"] = linkedMapOf(
                "online" to server.playerCount,
                "byBackend" to server.allPlayers
                    .groupingBy { it.currentServer.map { connection -> connection.serverInfo.name }.orElse("[unassigned]") }
                    .eachCount(),
                "pingMs" to pingSummary(pings),
            )
            details["health"] = linkedMapOf(
                "status" to if (signals.isEmpty()) "healthy" else "attention",
                "signals" to signals,
            )
        }

    private fun pingSummary(values: List<Long>): Map<String, Any?> = linkedMapOf(
        "min" to values.minOrNull(),
        "average" to values.average().takeIf { values.isNotEmpty() },
        "max" to values.maxOrNull(),
        "p95" to percentile(values, 0.95),
    )

    private fun percentile(values: List<Long>, percentile: Double): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        return sorted[((sorted.size - 1) * percentile).toInt().coerceIn(0, sorted.lastIndex)]
    }

    private companion object {
        const val HIGH_PING_THRESHOLD_MS = 300L
    }
}
