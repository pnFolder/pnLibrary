package ru.privatenull.pnlibrary.bukkit

import org.bukkit.Bukkit
import ru.privatenull.pnlibrary.bukkit.compat.ServerCapabilities
import ru.privatenull.pnlibrary.spi.platform.DiagnosticDetailsCollector
import ru.privatenull.pnlibrary.spi.platform.PlatformSnapshot

/** Collects actionable Bukkit state without enumerating private or high-volume server data. */
internal class BukkitDiagnosticDetailsCollector {
    fun collect(snapshot: PlatformSnapshot, includeSensitive: Boolean): Map<String, Any?> =
        DiagnosticDetailsCollector { snapshot }.collect(includeSensitive) { details ->
            val server = Bukkit.getServer()
            val plugins = server.pluginManager.plugins
            val installedNames = plugins.map { it.name }.toSet()
            val missingDependencies = plugins.mapNotNull { installedPlugin ->
                val missing = installedPlugin.description.depend
                    .filterNot(installedNames::contains)
                    .sorted()
                missing.takeIf { it.isNotEmpty() }?.let {
                    linkedMapOf("plugin" to installedPlugin.name, "missing" to it)
                }
            }
            val pings = server.onlinePlayers.mapNotNull { player ->
                runCatching {
                    (player.javaClass.getMethod("getPing").invoke(player) as? Number)?.toDouble()
                }.getOrNull()
            }
            val tps = ServerCapabilities.getTPS()?.takeIf { it.isNotEmpty() }
            val p95 = percentile(pings, 0.95)
            val oneMinuteTps = tps?.firstOrNull()
            val signals = buildList<Map<String, Any?>> {
                if (missingDependencies.isNotEmpty()) {
                    add(linkedMapOf("code" to "missingRequiredDependencies", "count" to missingDependencies.size))
                }
                if (oneMinuteTps != null && oneMinuteTps < LOW_TPS_THRESHOLD) {
                    add(linkedMapOf("code" to "lowTps", "oneMinute" to oneMinuteTps))
                }
                if (p95 != null && p95 >= HIGH_PING_THRESHOLD_MS) {
                    add(linkedMapOf("code" to "highPlayerLatency", "p95Ms" to p95))
                }
            }

            details["server"] = linkedMapOf(
                "name" to server.name,
                "version" to server.version,
                "minecraftVersion" to ServerCapabilities.minecraftVersion,
                "onlineMode" to server.onlineMode,
                "maxPlayers" to server.maxPlayers,
                "folia" to ServerCapabilities.isFolia,
            )
            details["performance"] = linkedMapOf(
                "tps" to tps?.take(3),
                "onlinePlayers" to server.onlinePlayers.size,
                "pingMs" to pingSummary(pings),
            )
            details["worlds"] = server.worlds.map { world ->
                linkedMapOf<String, Any?>(
                    "name" to world.name,
                    "environment" to world.environment.name,
                    "players" to world.players.size,
                ).apply {
                    if (!ServerCapabilities.isFolia) {
                        this["loadedChunks"] = world.loadedChunks.size
                        this["entities"] = world.entities.size
                    }
                }
            }
            details["pluginStates"] = plugins.map { installedPlugin ->
                linkedMapOf(
                    "name" to installedPlugin.name,
                    "version" to installedPlugin.description.version,
                    "enabled" to installedPlugin.isEnabled,
                )
            }
            details["missingRequiredDependencies"] = missingDependencies
            details["health"] = linkedMapOf(
                "status" to if (signals.isEmpty()) "healthy" else "attention",
                "signals" to signals,
            )
        }

    private fun pingSummary(values: List<Double>): Map<String, Any?> = linkedMapOf(
        "min" to values.minOrNull(),
        "average" to values.average().takeIf { values.isNotEmpty() },
        "max" to values.maxOrNull(),
        "p95" to percentile(values, 0.95),
    )

    private fun percentile(values: List<Double>, percentile: Double): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        return sorted[((sorted.size - 1) * percentile).toInt().coerceIn(0, sorted.lastIndex)]
    }

    private companion object {
        const val LOW_TPS_THRESHOLD = 18.0
        const val HIGH_PING_THRESHOLD_MS = 300.0
    }
}
