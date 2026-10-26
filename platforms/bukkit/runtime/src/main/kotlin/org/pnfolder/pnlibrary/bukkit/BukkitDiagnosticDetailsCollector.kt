package org.pnfolder.pnlibrary.bukkit

import org.bukkit.Bukkit
import org.pnfolder.pnlibrary.bukkit.compat.ServerCapabilities
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.spi.platform.DiagnosticDetailsCollector
import org.pnfolder.pnlibrary.spi.platform.DiagnosticSections
import org.pnfolder.pnlibrary.spi.platform.PlatformSnapshot

/** Collects actionable Bukkit state without enumerating private or high-volume server data. */
internal class BukkitDiagnosticDetailsCollector {
    fun collect(snapshot: PlatformSnapshot, includeSensitive: Boolean): Map<String, Any?> =
        DiagnosticDetailsCollector { snapshot }.collect(includeSensitive) { details ->
            val server = Bukkit.getServer()
            val plugins = server.pluginManager.plugins
            val missingDependencies = DiagnosticSections.missingDependencies(
                required = plugins.associate { it.name to it.description.depend },
                installed = plugins.map { it.name }.toSet(),
            )
            val pings = server.onlinePlayers.mapNotNull { player ->
                runCatchingNonFatal {
                    (player.javaClass.getMethod("getPing").invoke(player) as? Number)?.toLong()
                }.getOrNull()
            }
            val tps = ServerCapabilities.getTPS()?.takeIf { it.isNotEmpty() }
            val oneMinuteTps = tps?.firstOrNull()
            val signals = DiagnosticSections.commonSignals(missingDependencies, pings).apply {
                if (oneMinuteTps != null && oneMinuteTps < LOW_TPS_THRESHOLD) {
                    add(linkedMapOf("code" to "lowTps", "oneMinute" to oneMinuteTps))
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
                "pingMs" to DiagnosticSections.pingSummary(pings),
            )
            details["worlds"] = server.worlds.map { world ->
                linkedMapOf<String, Any?>(
                    "name" to world.name,
                    "environment" to world.environment.name,
                    "players" to world.players.size,
                ).apply {
                    if (!ServerCapabilities.isFolia) {
                        count(world, "getChunkCount")?.let { this["loadedChunks"] = it }
                        count(world, "getEntityCount")?.let { this["entities"] = it }
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
            details["health"] = DiagnosticSections.health(signals)
        }

    /**
     * Reads a cheap counter exposed by Paper-based servers. `World.getEntities()` and `getLoadedChunks()` copy
     * every element on the main thread, so they are never used as a fallback; the value is simply omitted.
     */
    private fun count(world: Any, method: String): Int? = runCatchingNonFatal {
        (world.javaClass.getMethod(method).invoke(world) as? Number)?.toInt()
    }.getOrNull()

    private companion object {
        const val LOW_TPS_THRESHOLD = 18.0
    }
}
