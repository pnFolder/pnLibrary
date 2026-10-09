package ru.privatenull.pnlibrary.bukkit

import org.bukkit.Bukkit
import ru.privatenull.pnlibrary.bukkit.compat.ServerCapabilities
import java.io.File
import java.io.FileInputStream
import java.util.jar.JarFile
import java.security.MessageDigest
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
            putServerSettings(server)
            putPerformanceDetails()
            putSchedulerDetails()
            putCommandDetails()
            putServiceDetails()
            putEventListenerDetails()
            putWorldDetails(server)
            putPlayerDetails(server, includeSensitive)
            putPluginDetails(server, includeSensitive)
            putDependencyHealth(server)
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
                "worldType" to world.worldType.name,
                "hasStorm" to world.hasStorm(),
                "isThundering" to world.isThundering,
                "keepSpawnInMemory" to world.keepSpawnInMemory,
                "allowAnimals" to world.allowAnimals,
                "allowMonsters" to world.allowMonsters,
                "border" to reflectionOrNull {
                    val border = world.worldBorder
                    linkedMapOf(
                        "size" to border.size,
                        "damageAmount" to border.damageAmount,
                        "damageBuffer" to border.damageBuffer,
                        "warningDistance" to border.warningDistance,
                        "warningTime" to border.warningTime,
                    )
                },
                "gameRules" to world.gameRules.associateWith { rule -> world.getGameRuleValue(rule) },
            ).apply {
                if (ServerCapabilities.isFolia) {
                    this["regionData"] = FOLIA_REGION_UNAVAILABLE
                } else {
                    this["players"] = world.players.size
                    this["loadedChunks"] = world.loadedChunks.size
                    this["entities"] = world.entities.size
                    this["entitiesByType"] = world.entities
                        .groupingBy { it.type.name }
                        .eachCount()
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
        this["playersByWorld"] = server.onlinePlayers.groupingBy { it.world.name }.eachCount()
        this["playersByGameMode"] = server.onlinePlayers.groupingBy { it.gameMode.name }.eachCount()
        this["operatorCount"] = server.onlinePlayers.count { it.isOp }
        val pings = server.onlinePlayers.mapNotNull { player ->
            reflectionOrNull {
                (player.javaClass.getMethod("getPing").invoke(player) as? Number)?.toDouble()
            }
        }
        this["pingSummary"] = linkedMapOf(
            "sampleCount" to pings.size,
            "minimum" to pings.minOrNull(),
            "maximum" to pings.maxOrNull(),
            "average" to pings.takeIf { it.isNotEmpty() }?.average(),
        )
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

    private fun MutableMap<String, Any?>.putPluginDetails(
        server: org.bukkit.Server,
        includeSensitive: Boolean,
    ) {
        this["plugins"] = server.pluginManager.plugins.map { plugin ->
            val description = plugin.description
            linkedMapOf<String, Any?>(
                "name" to plugin.name,
                "version" to description.version,
                "enabled" to plugin.isEnabled,
                "mainClass" to description.main,
                "authors" to description.authors,
                "depends" to description.depend,
                "softDepends" to description.softDepend,
                "loadBefore" to description.loadBefore,
                "classLoader" to plugin.javaClass.classLoader?.javaClass?.name,
                "permissions" to linkedMapOf<String, Any?>(
                    "count" to description.permissions.size,
                    "byDefault" to description.permissions
                        .groupingBy { it.default.name }
                        .eachCount(),
                    "names" to description.permissions.map { it.name }.sorted().take(MAX_PERMISSION_NAMES),
                ),
                "commands" to linkedMapOf<String, Any?>(
                    "count" to description.commands.size,
                    "names" to description.commands.keys.sorted().take(MAX_COMMAND_NAMES),
                    "aliases" to description.commands.values
                        .flatMap { it["aliases"] as? Iterable<*> ?: emptyList<Any>() }
                        .mapNotNull { it?.toString() }
                        .distinct()
                        .sorted()
                        .take(MAX_COMMAND_NAMES),
                ),
            ).apply {
                if (includeSensitive) {
                    plugin.javaClass.protectionDomain?.codeSource?.location?.toString()?.let { source ->
                        this["codeSource"] = source
                    }
                } else {
                    this["codeSource"] = "[REDACTED]"
                }
                pluginJar(plugin)?.let { jar ->
                    this["jarSizeBytes"] = jar.length()
                    this["lastModifiedUtc"] = Instant.ofEpochMilli(jar.lastModified()).toString()
                    sha256(jar)?.let { hash -> this["jarSha256"] = hash }
                    manifestDetails(jar)?.let { manifest -> this["manifest"] = manifest }
                }
                plugin.dataFolder.takeIf(File::exists)?.let { folder ->
                    this["dataFolder"] = linkedMapOf(
                        "exists" to true,
                        "fileCount" to folder.walkTopDown().count(),
                        "totalBytes" to folder.walkTopDown()
                            .filter(File::isFile)
                            .sumOf(File::length),
                        "configPresent" to File(folder, "config.yml").isFile,
                    )
                } ?: run {
                    this["dataFolder"] = linkedMapOf("exists" to false)
                }
            }
        }
    }

    private fun MutableMap<String, Any?>.putServerSettings(server: org.bukkit.Server) {
        this["serverSettings"] = linkedMapOf(
            "port" to server.port,
            "viewDistance" to server.viewDistance,
            "onlineMode" to server.onlineMode,
            "whitelistEnabled" to server.hasWhitelist(),
            "whitelistedPlayerCount" to server.whitelistedPlayers.size,
            "spawnRadius" to server.spawnRadius,
            "runtimeFlags" to linkedMapOf(
                "allowFlight" to reflectionOrNull { server.javaClass.getMethod("getAllowFlight").invoke(server) },
                "generateStructures" to reflectionOrNull {
                    server.javaClass.getMethod("getGenerateStructures").invoke(server)
                },
                "idleTimeoutMinutes" to reflectionOrNull {
                    server.javaClass.getMethod("getIdleTimeout").invoke(server)
                },
            ),
            "spawnSettings" to linkedMapOf(
                "animals" to spawnSettings(
                    server.getAnimalSpawnLimit(),
                    server.getTicksPerAnimalSpawns(),
                ),
                "monsters" to spawnSettings(
                    server.getMonsterSpawnLimit(),
                    server.getTicksPerMonsterSpawns(),
                ),
            ),
        )
    }

    private fun spawnSettings(limit: Int, intervalTicks: Int): Map<String, Int> =
        linkedMapOf(
            "limit" to limit,
            "intervalTicks" to intervalTicks,
        )

    private fun MutableMap<String, Any?>.putSchedulerDetails() {
        val scheduler = Bukkit.getScheduler()
        val pending = scheduler.pendingTasks
        val active = scheduler.activeWorkers
        this["scheduler"] = linkedMapOf(
            "pendingCount" to pending.size,
            "activeWorkerCount" to active.size,
            "pendingSyncCount" to pending.count { it.isSync },
            "pendingAsyncCount" to pending.count { !it.isSync },
            "pendingByPlugin" to pending.groupingBy { it.owner.name }.eachCount(),
            "activeByPlugin" to active.groupingBy { it.owner.name }.eachCount(),
            "pendingTaskTypes" to pending.map { it.javaClass.name }.distinct().sorted().take(MAX_SCHEDULER_CLASSES),
        )
    }

    private fun MutableMap<String, Any?>.putCommandDetails() {
        val commands = reflectionOrNull {
            val commandMap = Bukkit.getServer().javaClass
                .getMethod("getCommandMap")
                .invoke(Bukkit.getServer())
            val knownCommands = commandMap.javaClass
                .getMethod("getKnownCommands")
                .invoke(commandMap) as? Map<*, *>
            knownCommands?.keys
                ?.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
                ?.distinct()
                ?.sorted()
        } ?: emptyList()
        this["commands"] = linkedMapOf(
            "registeredCount" to commands.size,
            "names" to commands.take(MAX_COMMAND_NAMES),
        )
    }

    private fun MutableMap<String, Any?>.putServiceDetails() {
        val services = Bukkit.getServicesManager().knownServices
        this["services"] = services.map { serviceType ->
            linkedMapOf<String, Any?>(
                "type" to serviceType.name,
                "registrations" to Bukkit.getServicesManager().getRegistrations(serviceType).map { registration ->
                    linkedMapOf(
                        "provider" to registration.provider.javaClass.name,
                        "plugin" to registration.plugin.name,
                        "priority" to registration.priority.name,
                    )
                },
            )
        }
        this["serviceSummary"] = linkedMapOf(
            "typeCount" to services.size,
            "registrationCount" to services.sumOf {
                Bukkit.getServicesManager().getRegistrations(it).size
            },
        )
    }

    private fun MutableMap<String, Any?>.putEventListenerDetails() {
        val listeners = org.bukkit.event.HandlerList.getHandlerLists()
            .flatMap { it.registeredListeners.toList() }
        this["eventListeners"] = linkedMapOf(
            "handlerListCount" to org.bukkit.event.HandlerList.getHandlerLists().size,
            "registeredCount" to listeners.size,
            "byPlugin" to listeners.groupingBy { it.plugin.name }.eachCount(),
            "byPriority" to listeners.groupingBy { it.priority.name }.eachCount(),
            "listenerTypes" to listeners.map { it.listener.javaClass.name }
                .distinct()
                .sorted()
                .take(MAX_LISTENER_TYPES),
        )
    }

    private fun sha256(file: File): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }.getOrNull()

    private fun manifestDetails(file: File): Map<String, String>? = runCatching {
        JarFile(file).use { jar ->
            val attributes = jar.manifest?.mainAttributes ?: return@use emptyMap()
            listOf(
                "Implementation-Title",
                "Implementation-Version",
                "Implementation-Vendor",
                "Specification-Version",
                "Build-Jdk-Spec",
                "Created-By",
            ).mapNotNull { key ->
                attributes.getValue(key)?.takeIf(String::isNotBlank)?.let { key to it.take(256) }
            }.toMap()
        }
    }.getOrNull()?.takeIf { it.isNotEmpty() }

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

    private fun MutableMap<String, Any?>.putDependencyHealth(server: org.bukkit.Server) {
        val installed = server.pluginManager.plugins.map { it.name }.toSet()
        this["dependencyHealth"] = server.pluginManager.plugins.map { plugin ->
            val description = plugin.description
            val missingRequired = description.depend.filterNot(installed::contains)
            val missingOptional = description.softDepend.filterNot(installed::contains)
            val missingLoadBefore = description.loadBefore.filterNot(installed::contains)
            linkedMapOf<String, Any?>(
                "plugin" to plugin.name,
                "enabled" to plugin.isEnabled,
                "missingRequired" to missingRequired,
                "missingOptional" to missingOptional,
                "missingLoadBeforeTargets" to missingLoadBefore,
                "healthy" to missingRequired.isEmpty(),
            )
        }
        this["dependencySummary"] = linkedMapOf(
            "pluginCount" to installed.size,
            "pluginsWithMissingRequired" to server.pluginManager.plugins.count {
                it.description.depend.any { dependency -> dependency !in installed }
            },
            "pluginsWithMissingOptional" to server.pluginManager.plugins.count {
                it.description.softDepend.any { dependency -> dependency !in installed }
            },
        )
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
        const val MAX_SCHEDULER_CLASSES = 128
        const val MAX_LISTENER_TYPES = 256
        const val MAX_PERMISSION_NAMES = 256
        const val MAX_COMMAND_NAMES = 256
        const val FOLIA_REGION_UNAVAILABLE = "[UNAVAILABLE: requires a region thread on Folia]"
        const val FOLIA_PLAYERS_UNAVAILABLE =
            "[UNAVAILABLE: player details require entity schedulers on Folia]"
        const val PLAYERS_REDACTED = "[REDACTED: available in encrypted report only]"
    }
}
