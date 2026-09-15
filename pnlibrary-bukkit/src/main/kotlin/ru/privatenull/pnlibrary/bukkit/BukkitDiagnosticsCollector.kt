package ru.privatenull.pnlibrary.bukkit

import org.bukkit.Bukkit
import ru.privatenull.pnlibrary.bukkit.compat.ServerCapabilities
import java.io.File
import java.time.Instant
import java.util.Locale

/**
 * Collects Bukkit-specific state for pnLibrary diagnostic reports.
 *
 * The caller is responsible for invoking [collect] from Bukkit's primary thread or Folia's global
 * scheduler. Entity and region-owned values are deliberately omitted on Folia because a global
 * diagnostic snapshot cannot safely enter every region synchronously.
 */
internal class BukkitDiagnosticsCollector {
    /**
     * Creates one immutable-style diagnostic snapshot.
     *
     * @param includeSensitive whether player names, UUIDs, and locations may be included
     */
    fun collect(includeSensitive: Boolean): Map<String, Any?> {
        val server = Bukkit.getServer()
        return linkedMapOf<String, Any?>().apply {
            putServerDetails(server)
            putPerformanceDetails()
            putWorldDetails(server)
            putPlayerDetails(server, includeSensitive)
            putPluginDetails(server)
            putPlaceholderApiDetails(server)
        }
    }

    private fun MutableMap<String, Any?>.putServerDetails(server: org.bukkit.Server) {
        this["serverName"] = server.name
        this["serverVersion"] = server.version
        this["bukkitVersion"] = server.bukkitVersion
        this["serverClass"] = server.javaClass.name
        this["isPaper"] = ServerCapabilities.isPaper
        this["isPurpur"] = ServerCapabilities.isPurpur
        this["isLeaf"] = ServerCapabilities.isLeaf
    }

    private fun MutableMap<String, Any?>.putPerformanceDetails() {
        val tps = ServerCapabilities.getTPS()?.takeIf { it.size >= TPS_WINDOW_COUNT } ?: return
        this["tps"] = linkedMapOf(
            "1m" to formatTps(tps[0]),
            "5m" to formatTps(tps[1]),
            "15m" to formatTps(tps[2]),
        )
    }

    private fun MutableMap<String, Any?>.putWorldDetails(server: org.bukkit.Server) {
        this["worlds"] = server.worlds.map { world ->
            linkedMapOf<String, Any?>(
                "name" to world.name,
                "environment" to world.environment.name,
                "difficulty" to world.difficulty.name,
            ).apply {
                if (ServerCapabilities.isFolia) {
                    this["regionData"] = FOLIA_REGION_UNAVAILABLE
                } else {
                    this["players"] = world.players.size
                    this["loadedChunks"] = world.loadedChunks.size
                    this["entities"] = world.entities.size
                    this["time"] = world.time
                }
            }
        }
    }

    private fun MutableMap<String, Any?>.putPlayerDetails(
        server: org.bukkit.Server,
        includeSensitive: Boolean,
    ) {
        this["onlinePlayersCount"] = server.onlinePlayers.size
        this["maxPlayers"] = server.maxPlayers
        this["onlinePlayers"] = when {
            ServerCapabilities.isFolia -> FOLIA_PLAYERS_UNAVAILABLE
            !includeSensitive -> PLAYERS_REDACTED
            else -> server.onlinePlayers.map { player ->
                linkedMapOf(
                    "name" to player.name,
                    "uuid" to player.uniqueId.toString(),
                    "world" to player.world.name,
                    "ping" to reflectionOrNull {
                        player.javaClass.getMethod("getPing").invoke(player)
                    },
                )
            }
        }
    }

    private fun MutableMap<String, Any?>.putPluginDetails(server: org.bukkit.Server) {
        this["plugins"] = server.pluginManager.plugins.map { plugin ->
            val description = plugin.description
            linkedMapOf<String, Any?>(
                "name" to plugin.name,
                "version" to description.version,
                "enabled" to plugin.isEnabled,
                "mainClass" to description.main,
                "authors" to description.authors,
            ).apply {
                pluginJar(plugin)?.let { jar ->
                    this["jarSizeBytes"] = jar.length()
                    this["lastModifiedUtc"] = Instant.ofEpochMilli(jar.lastModified()).toString()
                }
            }
        }
    }

    private fun MutableMap<String, Any?>.putPlaceholderApiDetails(server: org.bukkit.Server) {
        val plugin = server.pluginManager.getPlugin("PlaceholderAPI")
        this["placeholderApi"] = if (plugin?.isEnabled == true) {
            linkedMapOf(
                "installed" to true,
                "version" to plugin.description.version,
                "expansionsCount" to placeholderExpansionCount(),
            )
        } else {
            linkedMapOf("installed" to false)
        }
    }

    private fun pluginJar(plugin: org.bukkit.plugin.Plugin): File? = reflectionOrNull {
        val method = plugin.javaClass.getMethod("getFile").apply { isAccessible = true }
        (method.invoke(plugin) as? File)?.takeIf(File::exists)
    }

    private fun placeholderExpansionCount(): Int = reflectionOrNull {
        val api = Class.forName("me.clip.placeholderapi.PlaceholderAPI")
        val identifiers = api.getMethod("getRegisteredIdentifiers").invoke(null) as? Collection<*>
        identifiers?.size ?: 0
    } ?: 0

    private fun <T> reflectionOrNull(operation: () -> T): T? = try {
        operation()
    } catch (_: ReflectiveOperationException) {
        null
    } catch (_: SecurityException) {
        null
    } catch (_: LinkageError) {
        null
    } catch (_: ClassCastException) {
        null
    }

    private fun formatTps(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

    private companion object {
        const val TPS_WINDOW_COUNT = 3
        const val FOLIA_REGION_UNAVAILABLE = "[UNAVAILABLE: requires a region thread on Folia]"
        const val FOLIA_PLAYERS_UNAVAILABLE =
            "[UNAVAILABLE: player details require entity schedulers on Folia]"
        const val PLAYERS_REDACTED = "[REDACTED: available in encrypted report only]"
    }
}
