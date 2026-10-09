package ru.privatenull.pnlibrary.bukkit

import org.bukkit.Bukkit
import ru.privatenull.pnlibrary.bukkit.compat.ServerCapabilities
import java.io.File
import java.io.FileInputStream
import java.util.jar.JarFile
import java.security.MessageDigest
import java.time.Instant
import kotlin.time.TimeSource
import java.util.Locale
import java.util.Properties

/**
 * Collects Bukkit-specific state for pnLibrary diagnostic reports.
 *
 * The caller is responsible for invoking [collect] from Bukkit's primary thread or Folia's global
 * scheduler. Entity and region-owned values are deliberately omitted on Folia because a global
 * diagnostic snapshot cannot safely enter every region synchronously.
 */
internal class BukkitDiagnosticsCollector {
    private val collectionWarnings = linkedMapOf<String, Int>()
    private val collectionHistory = ArrayDeque<Map<String, Any?>>()
    /**
     * Creates one immutable-style diagnostic snapshot.
     *
     * @param includeSensitive whether player names, UUIDs, and locations may be included
     */
    fun collect(includeSensitive: Boolean): Map<String, Any?> {
        collectionWarnings.clear()
        val startedAt = Instant.now()
        val started = TimeSource.Monotonic.markNow()
        val server = Bukkit.getServer()
        return linkedMapOf<String, Any?>().apply {
            this["collection"] = linkedMapOf(
                "startedUtc" to startedAt.toString(),
                "thread" to Thread.currentThread().name,
                "primaryThread" to Bukkit.isPrimaryThread(),
                "folia" to ServerCapabilities.isFolia,
                "includeSensitive" to includeSensitive,
            )
            this["privacy"] = linkedMapOf(
                "sensitiveDataIncluded" to includeSensitive,
                "redactedSections" to if (includeSensitive) emptyList<String>() else listOf("players", "locations", "worldSpawns", "pluginPaths"),
                "playerIdentityFields" to listOf("name", "uuid", "ping", "world", "location"),
            )
            putServerDetails(server)
            putServerSettings(server)
            putServerProperties(server)
            putPerformanceDetails()
            putSchedulerDetails()
            putCommandDetails(server)
            putPermissionDetails(server)
            putServiceDetails()
            putEventListenerDetails()
            putWorldDetails(server, includeSensitive)
            putPlayerDetails(server, includeSensitive)
            putPluginDetails(server, includeSensitive)
            putDependencyHealth(server)
            putPlaceholderApiDetails(server)
            putAnalyticsDetails(server)
            putCoverageSummary()
            val durationMs = started.elapsedNow().inWholeMilliseconds
            (this["collection"] as? MutableMap<String, Any?>)?.set("durationMs", durationMs)
            this["collectionWarnings"] = collectionWarnings.toSortedMap()
            val sample = linkedMapOf<String, Any?>(
                "completedUtc" to Instant.now().toString(),
                "durationMs" to durationMs,
                "warningCount" to collectionWarnings.values.sum(),
                "status" to if (collectionWarnings.isEmpty()) "healthy" else "attention",
            )
            collectionHistory.addLast(sample)
            while (collectionHistory.size > 32) collectionHistory.removeFirst()
            this["collectionAnalytics"] = linkedMapOf(
                "sampleCount" to collectionHistory.size,
                "successfulSamples" to collectionHistory.count { it["status"] == "healthy" },
                "attentionSamples" to collectionHistory.count { it["status"] == "attention" },
                "lastDurationMs" to durationMs,
                "averageDurationMs" to collectionHistory.mapNotNull { (it["durationMs"] as? Number)?.toLong() }
                    .average().takeIf { collectionHistory.isNotEmpty() },
                "maxDurationMs" to collectionHistory.mapNotNull { (it["durationMs"] as? Number)?.toLong() }.maxOrNull(),
                "recent" to collectionHistory.toList(),
            )
        }
    }

    private fun MutableMap<String, Any?>.putCoverageSummary() {
        val worlds = this["worlds"] as? Collection<*> ?: emptyList<Any>()
        val plugins = this["plugins"] as? Collection<*> ?: emptyList<Any>()
        val listeners = (this["eventListeners"] as? Map<*, *>)?.get("registeredCount")
        val services = (this["serviceSummary"] as? Map<*, *>)?.get("registrationCount")
        val commands = (this["commands"] as? Map<*, *>)?.get("registeredCount")
        val permissions = (this["permissions"] as? Map<*, *>)?.get("registeredCount")
        this["coverage"] = linkedMapOf(
            "sections" to listOf(
                "privacy",
                "server",
                "serverSettings",
                "serverProperties",
                "performance",
                "tickTiming",
                "resourcePack",
                "scheduler",
                "commands",
                "permissions",
                "services",
                "eventListeners",
                "worlds",
                "worldSummary",
                "world.datapacks",
                "world.storage",
                "players",
                "plugins",
                "pluginSummary",
                "dependencyHealth",
                "dependencies",
                "registrations",
                "placeholderApi",
                "analytics",
                "collectionAnalytics",
            ),
            "worldCount" to worlds.size,
            "pluginCount" to plugins.size,
            "registeredListenerCount" to listeners,
            "serviceRegistrationCount" to services,
            "registeredCommandCount" to commands,
            "registeredPermissionCount" to permissions,
        )
    }

    /** Adds derived Bukkit health signals and resource totals to the raw snapshot. */
    private fun MutableMap<String, Any?>.putAnalyticsDetails(server: org.bukkit.Server) {
        val worlds = this["worlds"] as? Collection<*> ?: emptyList<Any>()
        val plugins = server.pluginManager.plugins
        val scheduler = this["scheduler"] as? Map<*, *>
        val tps = this["tps"] as? Map<*, *>
        val dependencies = this["dependencySummary"] as? Map<*, *>
        val commands = this["commands"] as? Map<*, *>
        val services = this["serviceSummary"] as? Map<*, *>
        val serviceEntries = (this["services"] as? Collection<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
        val events = this["eventListeners"] as? Map<*, *>
        val registrationConflicts = registrationConflictDetails(server)
        val dependencyGraph = dependencyGraphDetails(server)

        val signals = mutableListOf<Map<String, Any?>>()
        run {
            if (collectionWarnings.isNotEmpty()) {
                signals += linkedMapOf(
                    "code" to "collectionWarnings",
                    "severity" to "info",
                    "count" to collectionWarnings.values.sum(),
                )
            }
            val oneMinuteTps = (tps?.get("1m") as? String)?.toDoubleOrNull()
            if (oneMinuteTps != null && oneMinuteTps < LOW_TPS_THRESHOLD) {
                signals += linkedMapOf("code" to "lowTps", "severity" to "elevated", "value" to oneMinuteTps)
            }
            if ((dependencies?.get("pluginsWithMissingRequired") as? Number)?.toInt()?.let { it > 0 } == true) {
                signals += linkedMapOf("code" to "missingRequiredDependencies", "severity" to "critical")
            }
            if ((commands?.get("missingDeclared") as? Collection<*>)?.isNotEmpty() == true) {
                signals += linkedMapOf("code" to "missingCommands", "severity" to "warning")
            }
            if ((services?.get("multipleProviders") as? Collection<*>)?.isNotEmpty() == true) {
                signals += linkedMapOf("code" to "multipleServiceProviders", "severity" to "warning")
            }
            if ((events?.get("registeredCount") as? Number)?.toInt() == 0 && plugins.isNotEmpty()) {
                signals += linkedMapOf("code" to "noEventListeners", "severity" to "info")
            }
            if ((scheduler?.get("longDelayTaskCount") as? Number)?.toInt()?.let { it > 0 } == true) {
                signals += linkedMapOf("code" to "longDelayTasks", "severity" to "info")
            }
            if ((dependencyGraph["cycles"] as? Collection<*>)?.isNotEmpty() == true) {
                signals += linkedMapOf("code" to "dependencyCycles", "severity" to "critical")
            }
            if ((this["pluginSummary"] as? Map<*, *>)?.get("duplicateNames") is Collection<*> &&
                ((this["pluginSummary"] as? Map<*, *>)?.get("duplicateNames") as Collection<*>).isNotEmpty()) {
                signals += linkedMapOf("code" to "duplicatePluginNames", "severity" to "warning")
            }
            val commandConflicts = (registrationConflicts["commandAliasConflicts"] as? Map<*, *>)?.size ?: 0
            val permissionConflicts = (registrationConflicts["permissionConflicts"] as? Map<*, *>)?.size ?: 0
            if (commandConflicts > 0 || permissionConflicts > 0) {
                signals += linkedMapOf(
                    "code" to "registrationConflicts",
                    "severity" to "warning",
                    "commandAliases" to commandConflicts,
                    "permissions" to permissionConflicts,
                )
            }
        }

        val pluginData = plugins.mapNotNull { plugin ->
            (this["plugins"] as? Collection<*>)
                ?.filterIsInstance<Map<*, *>>()
                ?.firstOrNull { it["name"] == plugin.name }
                ?.get("dataFolder") as? Map<*, *>
        }
        val storage = worlds.mapNotNull { (it as? Map<*, *>)?.get("storage") as? Map<*, *> }
        val pluginDetails = (this["plugins"] as? Collection<*>)?.filterIsInstance<Map<*, *>>().orEmpty()
        val pingSummary = this["pingSummary"] as? Map<*, *>
        val eventListeners = this["eventListeners"] as? Map<*, *>
        val permissions = this["permissions"] as? Map<*, *>
        val pluginHealth = plugins.map { plugin ->
            val detail = pluginDetails.firstOrNull { it["name"] == plugin.name }
            val missingRequired = plugin.description.depend.filterNot(plugins.map { it.name }::contains)
            val dataFolder = detail?.get("dataFolder") as? Map<*, *>
            linkedMapOf<String, Any?>(
                "name" to plugin.name,
                "enabled" to plugin.isEnabled,
                "status" to when {
                    !plugin.isEnabled -> "disabled"
                    missingRequired.isNotEmpty() -> "missingDependencies"
                    else -> "healthy"
                },
                "missingRequired" to missingRequired,
                "commands" to ((detail?.get("commands") as? Map<*, *>)?.get("count") as? Number),
                "permissions" to ((detail?.get("permissions") as? Map<*, *>)?.get("count") as? Number),
                "jarSizeBytes" to detail?.get("jarSizeBytes"),
                "dataFolderBytes" to dataFolder?.get("totalBytes"),
            )
        }
        val unhealthyPlugins = pluginHealth.count { it["status"] != "healthy" }
        if (unhealthyPlugins > 0) {
            signals += linkedMapOf("code" to "unhealthyPlugins", "severity" to "warning", "count" to unhealthyPlugins)
        }
        this["analytics"] = linkedMapOf(
            "status" to when {
                signals.any { it["severity"] == "critical" } -> "critical"
                signals.any { it["severity"] == "elevated" || it["severity"] == "warning" } -> "attention"
                else -> "healthy"
            },
            "signals" to signals,
            "resources" to linkedMapOf(
                "onlinePlayers" to server.onlinePlayers.size,
                "loadedWorlds" to worlds.size,
                "loadedChunks" to worlds.sumOf { ((it as? Map<*, *>)?.get("loadedChunks") as? Number)?.toLong() ?: 0L },
                "entities" to worlds.sumOf { ((it as? Map<*, *>)?.get("entities") as? Number)?.toLong() ?: 0L },
                "worldStorageBytes" to storage.sumOf { (it["totalBytes"] as? Number)?.toLong() ?: 0L },
                "pluginDataBytes" to pluginData.sumOf { (it["totalBytes"] as? Number)?.toLong() ?: 0L },
                "pendingTasks" to scheduler?.get("pendingCount"),
                "activeWorkers" to scheduler?.get("activeWorkerCount"),
                "repeatingTasks" to scheduler?.get("repeatingTaskCount"),
                "longDelayTasks" to scheduler?.get("longDelayTaskCount"),
            ),
            "counts" to linkedMapOf(
                "plugins" to plugins.size,
                "disabledPlugins" to plugins.count { !it.isEnabled },
                "worlds" to worlds.size,
                "commands" to commands?.get("registeredCount"),
                "permissions" to (this["permissions"] as? Map<*, *>)?.get("registeredCount"),
                "services" to services?.get("registrationCount"),
                "eventListeners" to events?.get("registeredCount"),
                "dependencyCycles" to (dependencyGraph["cycles"] as? Collection<*>)?.size,
                "orphanedOptionalDependencies" to (dependencyGraph["orphanedOptionalDependencies"] as? Collection<*>)?.size,
                "duplicatePluginNames" to ((this["pluginSummary"] as? Map<*, *>)?.get("duplicateNames") as? Collection<*>)?.size,
                "commandAliasConflicts" to (registrationConflicts["commandAliasConflicts"] as? Map<*, *>)?.size,
                "permissionConflicts" to (registrationConflicts["permissionConflicts"] as? Map<*, *>)?.size,
                "collectionWarnings" to collectionWarnings.values.sum(),
            ),
            "registrationConflicts" to registrationConflicts,
            "dependencies" to dependencyGraph,
            "pluginHealth" to pluginHealth,
            "unhealthyPlugins" to unhealthyPlugins,
            "playerDistribution" to linkedMapOf(
                "byWorld" to this["playersByWorld"],
                "byGameMode" to this["playersByGameMode"],
                "byLocale" to this["playersByLocale"],
                "pingBuckets" to pingSummary?.get("buckets"),
            ),
            "eventHandlers" to linkedMapOf(
                "byPlugin" to eventListeners?.get("handlersByPlugin"),
                "byPriority" to eventListeners?.get("byPriority"),
                "eventTypes" to eventListeners?.get("eventTypes"),
                "handlerMethodCount" to eventListeners?.get("handlerMethodCount"),
            ),
            "registrations" to linkedMapOf(
                "conflicts" to registrationConflicts,
                "servicesByPlugin" to serviceEntries
                    .flatMap { (it["registrations"] as? Collection<*>)?.filterIsInstance<Map<*, *>>().orEmpty() }
                    .mapNotNull { it["plugin"]?.toString() }
                    .groupingBy { it }
                    .eachCount(),
                "serviceProviderCount" to services?.get("registrationCount"),
                "permissionsByDefault" to permissions?.get("byDefault"),
                "permissionCount" to permissions?.get("registeredCount"),
            ),
        )
    }

    private fun dependencyGraphDetails(server: org.bukkit.Server): Map<String, Any?> {
        val plugins = server.pluginManager.plugins
        val names = plugins.map { it.name }.toSet()
        val required = plugins.associate { plugin ->
            plugin.name to plugin.description.depend.filter(names::contains).sorted()
        }
        val optionalAll = plugins.associate { plugin ->
            plugin.name to plugin.description.softDepend.sorted()
        }
        val optional = optionalAll.mapValues { (_, dependencies) -> dependencies.filter(names::contains) }
        val reverse = required.entries
            .flatMap { (plugin, dependencies) -> dependencies.map { dependency -> dependency to plugin } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, dependants) -> dependants.distinct().sorted() }
            .toSortedMap()
        return linkedMapOf(
            "required" to required.toSortedMap(),
            "optional" to optional.toSortedMap(),
            "reverseRequired" to reverse,
            "cycles" to dependencyCycles(required),
            "orphanedOptionalDependencies" to optionalAll.flatMap { (plugin, dependencies) ->
                dependencies.filterNot(names::contains).map { dependency -> "$plugin:$dependency" }
            },
        )
    }

    private fun dependencyCycles(graph: Map<String, List<String>>): List<List<String>> {
        val cycles = linkedSetOf<List<String>>()
        val visiting = linkedSetOf<String>()
        val visited = linkedSetOf<String>()

        fun visit(node: String, path: List<String>) {
            if (node in visiting) {
                val start = path.indexOf(node)
                if (start >= 0) {
                    cycles += (path.drop(start) + node).distinct()
                }
                return
            }
            if (!visited.add(node)) return
            visiting += node
            graph[node].orEmpty().forEach { dependency -> visit(dependency, path + dependency) }
            visiting -= node
        }

        graph.keys.forEach { visit(it, listOf(it)) }
        return cycles.take(MAX_DEPENDENCY_CYCLES)
    }

    private fun registrationConflictDetails(server: org.bukkit.Server): Map<String, Any?> {
        val declaredCommands: List<Pair<String, String>> = server.pluginManager.plugins.flatMap { plugin ->
            plugin.description.commands.entries.flatMap { entry ->
                val name = entry.key?.toString()?.trim()?.lowercase(Locale.ROOT)
                    ?: return@flatMap emptyList()
                val aliases: List<String> = ((entry.value["aliases"] as? Iterable<*>)
                    ?: emptyList<Any?>())
                    .mapNotNull { alias -> alias?.toString()?.trim()?.lowercase(Locale.ROOT) }
                (listOf(name) + aliases).map { it to plugin.name }
            }
        }
        val declaredPermissions: List<Pair<String, String>> = server.pluginManager.plugins.flatMap { plugin ->
            plugin.description.permissions.map { permission -> permission.name.lowercase(Locale.ROOT) to plugin.name }
        }
        return linkedMapOf(
            "commandAliasConflicts" to declaredCommands
                .groupBy({ it.first }, { it.second })
                .filterValues { it.distinct().size > 1 }
                .mapValues { (_, owners) -> owners.distinct().sorted() }
                .toSortedMap(),
            "permissionConflicts" to declaredPermissions
                .groupBy({ it.first }, { it.second })
                .filterValues { it.distinct().size > 1 }
                .mapValues { (_, owners) -> owners.distinct().sorted() }
                .toSortedMap(),
            "commandsByPlugin" to declaredCommands.groupingBy { it.second }.eachCount(),
            "permissionsByPlugin" to declaredPermissions.groupingBy { it.second }.eachCount(),
            "listenersByPlugin" to listenerOwners()
                .groupingBy { it }
                .eachCount(),
        )
    }

    private fun listenerOwners(): List<String> =
        org.bukkit.event.HandlerList.getHandlerLists()
            .flatMap { it.registeredListeners.toList() }
            .map { it.plugin.name }

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
        ServerCapabilities.getTPS()?.takeIf { it.size >= TPS_WINDOW_COUNT }?.let { tps ->
            this["tps"] = linkedMapOf(
                "1m" to formatTps(tps[0]),
                "5m" to formatTps(tps[1]),
                "15m" to formatTps(tps[2]),
            )
        }
        this["tickTiming"] = linkedMapOf(
            "currentTick" to reflectionOrNull {
                Bukkit.getServer().javaClass.getMethod("getCurrentTick").invoke(Bukkit.getServer())
            },
            "averageTickTimeNanos" to reflectionOrNull {
                Bukkit.getServer().javaClass.getMethod("getAverageTickTime").invoke(Bukkit.getServer())
            },
            "currentTickTimeNanos" to reflectionOrNull {
                Bukkit.getServer().javaClass.getMethod("getCurrentTickTime").invoke(Bukkit.getServer())
            },
        )
    }

    private fun MutableMap<String, Any?>.putWorldDetails(
        server: org.bukkit.Server,
        includeSensitive: Boolean,
    ) {
        val worlds = server.worlds.map { world ->
            linkedMapOf<String, Any?>(
                "name" to world.name,
                "uid" to world.uid.toString(),
                "environment" to world.environment.name,
                "difficulty" to world.difficulty.name,
                "worldType" to world.worldType.name,
                "generator" to world.generator?.javaClass?.name,
                "hasStorm" to world.hasStorm(),
                "isThundering" to world.isThundering,
                "keepSpawnInMemory" to world.keepSpawnInMemory,
                "allowAnimals" to world.allowAnimals,
                "allowMonsters" to world.allowMonsters,
                "autoSave" to reflectionOrNull { world.javaClass.getMethod("isAutoSave").invoke(world) },
                "pvp" to reflectionOrNull { world.javaClass.getMethod("isPVP").invoke(world) },
                "datapacks" to datapackDetails(world),
                "storage" to worldStorageDetails(world),
                "spawn" to if (includeSensitive) {
                    world.spawnLocation.let { location ->
                        linkedMapOf(
                            "x" to location.x,
                            "y" to location.y,
                            "z" to location.z,
                            "yaw" to location.yaw,
                            "pitch" to location.pitch,
                        )
                    }
                } else {
                    "[REDACTED: world spawn coordinates]"
                },
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
                    this["entityDensityPerChunk"] = world.loadedChunks.size.takeIf { it > 0 }
                        ?.let { world.entities.size.toDouble() / it }
                    this["loadBucket"] = when {
                        world.loadedChunks.size == 0 -> "empty"
                        world.loadedChunks.size < 100 -> "light"
                        world.loadedChunks.size < 500 -> "moderate"
                        else -> "heavy"
                    }
                    this["entitiesByType"] = world.entities
                        .groupingBy { it.type.name }
                        .eachCount()
                    this["time"] = world.time
                }
            }
        }
        this["worlds"] = worlds
        this["worldSummary"] = linkedMapOf(
            "worldCount" to worlds.size,
            "stormingWorldCount" to worlds.count { it["hasStorm"] == true },
            "thunderingWorldCount" to worlds.count { it["isThundering"] == true },
            "players" to worlds.sumOf { (it["players"] as? Number)?.toInt() ?: 0 },
            "loadedChunks" to worlds.sumOf { (it["loadedChunks"] as? Number)?.toInt() ?: 0 },
            "entities" to worlds.sumOf { (it["entities"] as? Number)?.toInt() ?: 0 },
            "byLoadBucket" to worlds.groupingBy { it["loadBucket"]?.toString() ?: "unknown" }.eachCount(),
            "highestEntityDensity" to worlds
                .maxByOrNull { (it["entityDensityPerChunk"] as? Number)?.toDouble() ?: 0.0 }
                ?.let { linkedMapOf("world" to it["name"], "entitiesPerChunk" to it["entityDensityPerChunk"]) },
            "entitiesByType" to worlds
                .mapNotNull { it["entitiesByType"] as? Map<*, *> }
                .flatMap { it.entries }
                .groupingBy { it.key.toString() }
                .fold(0L) { total, entry -> total + ((entry.value as? Number)?.toLong() ?: 0L) }
                .toSortedMap(),
        )
    }

    private fun MutableMap<String, Any?>.putPlayerDetails(
        server: org.bukkit.Server,
        includeSensitive: Boolean,
    ) {
        this["onlinePlayersCount"] = server.onlinePlayers.size
        this["maxPlayers"] = server.maxPlayers
        this["playersByWorld"] = server.onlinePlayers.groupingBy { it.world.name }.eachCount()
        this["playersByGameMode"] = server.onlinePlayers.groupingBy { it.gameMode.name }.eachCount()
        this["playersByLocale"] = server.onlinePlayers
            .mapNotNull { player -> reflectionOrNull { player.javaClass.getMethod("getLocale").invoke(player)?.toString() } }
            .groupingBy { it }
            .eachCount()
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
            "buckets" to linkedMapOf(
                "under50ms" to pings.count { it < 50.0 },
                "50to99ms" to pings.count { it in 50.0..99.0 },
                "100to199ms" to pings.count { it in 100.0..199.0 },
                "200msOrMore" to pings.count { it >= 200.0 },
            ),
            "percentiles" to linkedMapOf(
                "p50" to percentile(pings, 0.50),
                "p95" to percentile(pings, 0.95),
                "p99" to percentile(pings, 0.99),
            ),
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
                    val config = File(folder, "config.yml")
                    val files = folder.walkTopDown().filter(File::isFile).toList()
                    this["dataFolder"] = linkedMapOf(
                        "exists" to true,
                        "fileCount" to files.size,
                        "totalBytes" to files.sumOf(File::length),
                        "fileTypes" to files
                            .groupingBy { fileExtension(it.name) }
                            .eachCount(),
                        "largestFiles" to files
                            .sortedByDescending(File::length)
                            .take(MAX_LARGEST_FILES)
                            .map { file ->
                                linkedMapOf(
                                    "name" to file.relativeTo(folder).path.replace(File.separatorChar, '/'),
                                    "sizeBytes" to file.length(),
                                )
                            },
                        "configPresent" to config.isFile,
                        "configSizeBytes" to config.takeIf(File::isFile)?.length(),
                        "configLastModifiedUtc" to config.takeIf(File::isFile)?.let {
                            Instant.ofEpochMilli(it.lastModified()).toString()
                        },
                        "configSha256" to config.takeIf(File::isFile)?.let(::sha256),
                    )
                } ?: run {
                    this["dataFolder"] = linkedMapOf("exists" to false)
                }
            }
        }
        val plugins = server.pluginManager.plugins
        this["pluginSummary"] = linkedMapOf(
            "total" to plugins.size,
            "enabled" to plugins.count { it.isEnabled },
            "disabled" to plugins.count { !it.isEnabled },
            "byClassLoader" to plugins
                .groupingBy { it.javaClass.classLoader?.javaClass?.name ?: "unknown" }
                .eachCount(),
            "duplicateNames" to plugins
                .groupingBy { it.name.lowercase(Locale.ROOT) }
                .eachCount()
                .filterValues { it > 1 }
                .keys
                .sorted(),
            "loadOrder" to plugins.map { it.name },
            "dependencyCounts" to linkedMapOf(
                "required" to plugins.sumOf { it.description.depend.size },
                "optional" to plugins.sumOf { it.description.softDepend.size },
                "loadBefore" to plugins.sumOf { it.description.loadBefore.size },
            ),
            "commandCount" to plugins.sumOf { it.description.commands.size },
            "permissionCount" to plugins.sumOf { it.description.permissions.size },
            "totalJarBytes" to (this["plugins"] as? Collection<*>)
                ?.mapNotNull { (it as? Map<*, *>)?.get("jarSizeBytes") as? Number }
                ?.sumOf { it.toLong() },
        )
    }

    private fun MutableMap<String, Any?>.putServerSettings(server: org.bukkit.Server) {
        this["serverSettings"] = linkedMapOf(
            "port" to server.port,
            "motd" to reflectionOrNull { server.javaClass.getMethod("getMotd").invoke(server) },
            "maxPlayers" to server.maxPlayers,
            "viewDistance" to server.viewDistance,
            "simulationDistance" to reflectionOrNull {
                server.javaClass.getMethod("getSimulationDistance").invoke(server)
            },
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
            "resourcePack" to linkedMapOf(
                "url" to reflectionOrNull { server.javaClass.getMethod("getResourcePack").invoke(server) },
                "hash" to reflectionOrNull { server.javaClass.getMethod("getResourcePackHash").invoke(server) },
                "required" to reflectionOrNull {
                    server.javaClass.getMethod("isResourcePackRequired").invoke(server)
                },
                "prompt" to reflectionOrNull {
                    server.javaClass.getMethod("getResourcePackPrompt").invoke(server)?.toString()
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

    private fun MutableMap<String, Any?>.putServerProperties(server: org.bukkit.Server) {
        val file = reflectionOrNull {
            server.javaClass.getMethod("getWorldContainer").invoke(server) as? File
        }?.let { File(it, "server.properties") }?.takeIf(File::isFile)
        val properties = file?.let { source ->
            runCatching {
                Properties().apply {
                    source.inputStream().use(::load)
                }.entries
                    .associate { (key, value) ->
                        key.toString() to if (isSensitiveProperty(key.toString())) "[REDACTED]" else value.toString()
                    }
                    .toSortedMap()
                    .entries
                    .take(MAX_SERVER_PROPERTIES)
                    .associate { it.toPair() }
            }.getOrNull()
        }
        this["serverProperties"] = linkedMapOf(
            "filePresent" to (file != null),
            "sizeBytes" to file?.length(),
            "lastModifiedUtc" to file?.let { Instant.ofEpochMilli(it.lastModified()).toString() },
            "sha256" to file?.let(::sha256),
            "propertyCount" to (properties?.size ?: 0),
            "values" to (properties ?: emptyMap<String, String>()),
        )
    }

    private fun isSensitiveProperty(key: String): Boolean =
        key.lowercase(Locale.ROOT).contains("password") ||
            key.lowercase(Locale.ROOT).contains("token") ||
            key.lowercase(Locale.ROOT).contains("secret")

    private fun fileExtension(name: String): String =
        name.substringAfterLast('.', "[none]").lowercase(Locale.ROOT)

    private fun spawnSettings(limit: Int, intervalTicks: Int): Map<String, Int> =
        linkedMapOf(
            "limit" to limit,
            "intervalTicks" to intervalTicks,
        )

    private fun datapackDetails(world: org.bukkit.World): Map<String, Any?> {
        val worldFolder = reflectionOrNull {
            world.javaClass.getMethod("getWorldFolder").invoke(world) as? File
        }
        val folder = worldFolder?.resolve("datapacks")?.takeIf(File::isDirectory)
        val entries = folder?.listFiles()?.filter { it.isFile || it.isDirectory }.orEmpty()
        return linkedMapOf(
            "folderPresent" to (folder != null),
            "count" to entries.size,
            "entries" to entries.sortedBy { it.name.lowercase(Locale.ROOT) }.take(MAX_DATAPACKS).map { entry ->
                linkedMapOf(
                    "name" to entry.name,
                    "type" to if (entry.isDirectory) "directory" else "file",
                    "sizeBytes" to if (entry.isFile) entry.length() else null,
                    "lastModifiedUtc" to Instant.ofEpochMilli(entry.lastModified()).toString(),
                )
            },
        )
    }

    private fun worldStorageDetails(world: org.bukkit.World): Map<String, Any?> {
        val folder = reflectionOrNull {
            world.javaClass.getMethod("getWorldFolder").invoke(world) as? File
        }?.takeIf(File::isDirectory)
        val files = folder?.walkTopDown()?.filter(File::isFile)?.toList().orEmpty()
        val regionFiles = folder?.resolve("region")?.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".mca", ignoreCase = true) }
            .orEmpty()
        return linkedMapOf(
            "folderPresent" to (folder != null),
            "fileCount" to files.size,
            "totalBytes" to files.sumOf(File::length),
            "regionFileCount" to regionFiles.size,
            "regionBytes" to regionFiles.sumOf(File::length),
            "lastModifiedUtc" to files.maxOfOrNull(File::lastModified)?.let(Instant::ofEpochMilli)?.toString(),
        )
    }

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
            "pendingTasks" to pending
                .sortedWith(compareBy({ it.owner.name.lowercase(Locale.ROOT) }, { it.taskId }))
                .take(MAX_PENDING_TASKS)
                .map { task ->
                    val nextRun = taskLong(task, "getNextRun")
                    val period = taskLong(task, "getPeriod")
                    linkedMapOf<String, Any?>(
                        "id" to task.taskId,
                        "plugin" to task.owner.name,
                        "sync" to task.isSync,
                        "nextRunTick" to nextRun,
                        "periodTicks" to period,
                        "type" to task.javaClass.name,
                    )
                },
            "repeatingTaskCount" to pending.count { (taskLong(it, "getPeriod") ?: 0L) > 0 },
            "longDelayTaskCount" to pending.count { (taskLong(it, "getNextRun") ?: 0L) > LONG_DELAY_TICKS },
        )
    }

    private fun taskLong(task: org.bukkit.scheduler.BukkitTask, method: String): Long? =
        reflectionOrNull { (task.javaClass.getMethod(method).invoke(task) as? Number)?.toLong() }

    private fun MutableMap<String, Any?>.putCommandDetails(server: org.bukkit.Server) {
        val commands = reflectionOrNull {
            val commandMap = server.javaClass
                .getMethod("getCommandMap")
                .invoke(server)
            val knownCommands = commandMap.javaClass
                .getMethod("getKnownCommands")
                .invoke(commandMap) as? Map<*, *>
            knownCommands?.keys
                ?.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) }
                ?.distinct()
                ?.sorted()
        } ?: emptyList()
        val declared = server.pluginManager.plugins.flatMap { plugin ->
            plugin.description.commands.keys.map { command ->
                plugin.name to command.lowercase(Locale.ROOT)
            }
        }
        val registered = commands.toSet()
        this["commands"] = linkedMapOf(
            "registeredCount" to commands.size,
            "names" to commands.take(MAX_COMMAND_NAMES),
            "declaredCount" to declared.size,
            "declaredByPlugin" to declared
                .groupBy({ it.first }, { it.second }),
            "duplicateDeclared" to declared
                .groupingBy { it.second }
                .eachCount()
                .filterValues { it > 1 }
                .keys
                .sorted()
                .take(MAX_COMMAND_NAMES),
            "missingDeclared" to declared
                .filterNot { (_, command) -> command in registered }
                .map { (plugin, command) -> "$plugin:$command" }
                .take(MAX_COMMAND_NAMES),
        )
    }

    private fun MutableMap<String, Any?>.putPermissionDetails(server: org.bukkit.Server) {
        val permissions = reflectionOrNull {
            val values = server.pluginManager.javaClass
                .getMethod("getPermissions")
                .invoke(server.pluginManager) as? Collection<*>
            values.orEmpty().filterNotNull()
        }.orEmpty()
        this["permissions"] = linkedMapOf(
            "registeredCount" to permissions.size,
            "byDefault" to permissions
                .mapNotNull { permission ->
                    reflectionOrNull {
                        permission.javaClass.getMethod("getDefault").invoke(permission).toString()
                    }
                }
                .groupingBy { it }
                .eachCount(),
            "names" to permissions.mapNotNull { permission ->
                reflectionOrNull { permission.javaClass.getMethod("getName").invoke(permission).toString() }
            }.distinct().sorted().take(MAX_PERMISSION_NAMES),
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
            "registrationCount" to services.sumOf { Bukkit.getServicesManager().getRegistrations(it).size },
            "byPriority" to services
                .flatMap { Bukkit.getServicesManager().getRegistrations(it) }
                .groupingBy { it.priority.name }
                .eachCount(),
            "multipleProviders" to services
                .filter { Bukkit.getServicesManager().getRegistrations(it).size > 1 }
                .map { it.name }
                .sorted(),
        )
    }

    private fun MutableMap<String, Any?>.putEventListenerDetails() {
        val listeners = org.bukkit.event.HandlerList.getHandlerLists()
            .flatMap { it.registeredListeners.toList() }
        val handlers = listeners.flatMap { registered ->
            registered.listener.javaClass.methods.mapNotNull { method ->
                method.getAnnotation(org.bukkit.event.EventHandler::class.java)?.let { registered.plugin.name to method }
            }
        }
        this["eventListeners"] = linkedMapOf(
            "handlerListCount" to org.bukkit.event.HandlerList.getHandlerLists().size,
            "registeredCount" to listeners.size,
            "byPlugin" to listeners.groupingBy { it.plugin.name }.eachCount(),
            "byPriority" to listeners.groupingBy { it.priority.name }.eachCount(),
            "listenerTypes" to listeners.map { it.listener.javaClass.name }
                .distinct()
                .sorted()
                .take(MAX_LISTENER_TYPES),
            "handlerMethodCount" to handlers.size,
            "handlersByPlugin" to handlers.groupingBy { it.first }.eachCount(),
            "eventTypes" to handlers.mapNotNull { (_, method) ->
                method.parameterTypes.firstOrNull()?.name
            }.distinct().sorted().take(MAX_EVENT_TYPES),
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
    } catch (error: ReflectiveOperationException) {
        recordCollectionWarning(error)
        null
    } catch (error: SecurityException) {
        recordCollectionWarning(error)
        null
    } catch (error: LinkageError) {
        recordCollectionWarning(error)
        null
    } catch (error: ClassCastException) {
        recordCollectionWarning(error)
        null
    }

    private fun recordCollectionWarning(error: Throwable) {
        val key = error.javaClass.simpleName.ifBlank { "Unknown" }
        collectionWarnings[key] = (collectionWarnings[key] ?: 0) + 1
    }

    private fun formatTps(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

    private fun percentile(values: List<Double>, percentile: Double): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val index = ((sorted.size - 1) * percentile).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    private companion object {
        const val TPS_WINDOW_COUNT = 3
        const val LOW_TPS_THRESHOLD = 18.0
        const val MAX_SCHEDULER_CLASSES = 128
        const val MAX_PENDING_TASKS = 256
        const val LONG_DELAY_TICKS = 20L * 60L * 5L
        const val MAX_LISTENER_TYPES = 256
        const val MAX_EVENT_TYPES = 256
        const val MAX_PERMISSION_NAMES = 256
        const val MAX_COMMAND_NAMES = 256
        const val MAX_SERVER_PROPERTIES = 256
        const val MAX_LARGEST_FILES = 16
        const val MAX_DEPENDENCY_CYCLES = 64
        const val MAX_DATAPACKS = 128
        const val FOLIA_REGION_UNAVAILABLE = "[UNAVAILABLE: requires a region thread on Folia]"
        const val FOLIA_PLAYERS_UNAVAILABLE =
            "[UNAVAILABLE: player details require entity schedulers on Folia]"
        const val PLAYERS_REDACTED = "[REDACTED: available in encrypted report only]"
    }
}
