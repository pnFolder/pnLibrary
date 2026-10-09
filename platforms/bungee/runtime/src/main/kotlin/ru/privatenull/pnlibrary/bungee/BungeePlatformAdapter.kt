package ru.privatenull.pnlibrary.bungee

import net.md_5.bungee.api.chat.TextComponent
import net.md_5.bungee.api.plugin.Plugin
import ru.privatenull.pnlibrary.api.commands.CommandRegistration
import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.plugin.PluginMetadata
import ru.privatenull.pnlibrary.api.remote.RemotePolicyContext
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.bungee.commands.BungeeCommandAdapter
import ru.privatenull.pnlibrary.bungee.tasks.BungeeTaskAdapter
import ru.privatenull.pnlibrary.core.updates.ProxyUpdateCommand
import ru.privatenull.pnlibrary.spi.audiences.PlatformAudienceAdapter
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandAdapter
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.spi.platform.PlatformSnapshot
import ru.privatenull.pnlibrary.spi.platform.PluginSnapshot
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskAdapter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import kotlin.time.TimeSource
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/** Runtime adapter for BungeeCord-compatible proxy servers. */
internal class BungeePlatformAdapter(
    val plugin: Plugin,
) : PlatformAdapter {

    private val closed = AtomicBoolean()
    private val bound = AtomicBoolean()

    private var updateCommand: CommandRegistration? = null
    private var previousUncaughtHandler: Thread.UncaughtExceptionHandler? = null
    private var installedUncaughtHandler: Thread.UncaughtExceptionHandler? = null

    override val type: PlatformType = PlatformType.BUNGEECORD
    override val dataFolder = plugin.dataFolder.toPath()

    override val implementationName: String
        get() = plugin.proxy.name.ifBlank { type.displayName }

    override val metricsFactory: PlatformMetricsFactory =
        BungeeMetricsFactory()

    override val commandAdapter: PlatformCommandAdapter =
        BungeeCommandAdapter(plugin)

    override val audienceAdapter: PlatformAudienceAdapter =
        BungeeAudienceAdapter(plugin)

    override val taskAdapter: PlatformTaskAdapter =
        BungeeTaskAdapter(plugin)

    override val logHandler: (Any, LogLevel, String, Throwable?) -> Unit =
        { _, level, message, error ->
            val nativeLevel = when (level) {
                LogLevel.WARNING -> Level.WARNING
                LogLevel.ERROR -> Level.SEVERE
                else -> Level.INFO
            }

            plugin.logger.log(nativeLevel, message, error)
        }

    @Synchronized
    override fun observeNativeLogs(observer: ((Any, LogLevel, String, Throwable?) -> Unit)?) {
        if (observer != null && installedUncaughtHandler == null) {
            previousUncaughtHandler = Thread.getDefaultUncaughtExceptionHandler()
            installedUncaughtHandler = Thread.UncaughtExceptionHandler { thread, error ->
                observer(plugin, LogLevel.ERROR, "Uncaught exception on thread ${thread.name}", error)
                previousUncaughtHandler?.uncaughtException(thread, error)
            }
            Thread.setDefaultUncaughtExceptionHandler(installedUncaughtHandler)
        } else if (observer == null && installedUncaughtHandler != null) {
            if (Thread.getDefaultUncaughtExceptionHandler() === installedUncaughtHandler) {
                Thread.setDefaultUncaughtExceptionHandler(previousUncaughtHandler)
            }
            previousUncaughtHandler = null
            installedUncaughtHandler = null
        }
    }

    @Suppress("DEPRECATION")
    override fun console(owner: Any, message: String) {
        plugin.proxy.console.sendMessage(
            *TextComponent.fromLegacyText(message),
        )
    }

    override fun ownerMetadata(owner: Any): PluginSnapshot? {
        val target = owner as? Plugin
            ?: return unsupportedOwner(owner)

        return target.toSnapshot()
    }

    override fun installedPlugins(): Map<String, String> =
        plugin.proxy.pluginManager.plugins.associate { installedPlugin ->
            installedPlugin.description.name to installedPlugin.description.version
        }

    override fun snapshot(): PlatformSnapshot {
        val proxy = plugin.proxy

        return PlatformSnapshot(
            name = proxy.name,
            version = proxy.version,
            onlinePlayers = proxy.onlineCount,
            registeredServers = proxy.servers.keys.toList(),
            plugins = proxy.pluginManager.plugins.map { it.toSnapshot() },
        )
    }

    override fun diagnosticDetails(includeSensitive: Boolean): Map<String, Any?> {
        val started = TimeSource.Monotonic.markNow()
        val details = snapshot().asMap().toMutableMap()
        details["collection"] = linkedMapOf(
            "startedUtc" to java.time.Instant.now().toString(),
            "thread" to Thread.currentThread().name,
            "includeSensitive" to includeSensitive,
        )
        details["pluginDependencies"] = plugin.proxy.pluginManager.plugins.map { installedPlugin ->
            val metadata = installedPlugin.description
            linkedMapOf<String, Any?>(
                "id" to metadata.name,
                "depends" to metadata.depends.toList().sorted(),
                "softDepends" to metadata.softDepends.toList().sorted(),
                "libraries" to metadata.libraries.toList().sorted(),
            )
        }
        val installedPluginNames = plugin.proxy.pluginManager.plugins
            .map { it.description.name }
            .toSet()
        details["dependencyHealth"] = plugin.proxy.pluginManager.plugins.map { installedPlugin ->
            val metadata = installedPlugin.description
            val missingRequired = metadata.depends.filterNot(installedPluginNames::contains)
            val missingOptional = metadata.softDepends.filterNot(installedPluginNames::contains)
            linkedMapOf<String, Any?>(
                "plugin" to metadata.name,
                "missingRequired" to missingRequired.toList().sorted(),
                "missingOptional" to missingOptional.toList().sorted(),
                "healthy" to missingRequired.isEmpty(),
            )
        }
        details["pluginArtifacts"] = plugin.proxy.pluginManager.plugins.map { installedPlugin ->
            val metadata = installedPlugin.description
            val file = metadata.file
            linkedMapOf<String, Any?>(
                "id" to metadata.name,
                "path" to if (includeSensitive) file?.absolutePath else "[REDACTED]",
                "sizeBytes" to file?.takeIf { it.isFile }?.length(),
                "lastModifiedUtc" to file?.takeIf { it.isFile }?.let(::formatUtc),
                "sha256" to file?.takeIf { it.isFile }?.let(::sha256),
            )
        }
        details["pluginSummary"] = linkedMapOf(
            "total" to plugin.proxy.pluginManager.plugins.size,
            "versions" to plugin.proxy.pluginManager.plugins.associate {
                it.description.name to it.description.version
            },
            "duplicateNames" to plugin.proxy.pluginManager.plugins
                .groupingBy { it.description.name.lowercase() }
                .eachCount()
                .filterValues { it > 1 }
                .keys
                .sorted(),
            "byMainClass" to plugin.proxy.pluginManager.plugins
                .groupingBy { it.description.main }
                .eachCount(),
            "loadOrder" to plugin.proxy.pluginManager.plugins.map { it.description.name },
        )
        details["pluginHealth"] = plugin.proxy.pluginManager.plugins.map { installedPlugin ->
            val metadata = installedPlugin.description
            val health = (details["dependencyHealth"] as? Collection<*>)
                ?.filterIsInstance<Map<*, *>>()
                ?.firstOrNull { it["plugin"] == metadata.name }
            val missing = health?.get("missingRequired") as? Collection<*>
            linkedMapOf<String, Any?>(
                "id" to metadata.name,
                "version" to metadata.version,
                "status" to if (missing.isNullOrEmpty()) "healthy" else "missingDependencies",
                "missingRequired" to missing.orEmpty(),
                "optionalDependencies" to metadata.softDepends.size,
            )
        }
        val unhealthyPlugins = (details["pluginHealth"] as? Collection<*>)
            ?.filterIsInstance<Map<*, *>>()
            ?.count { it["status"] != "healthy" }
            ?: 0
        details["servers"] = plugin.proxy.servers.map { (name, info) ->
            val address = info.address
            linkedMapOf<String, Any?>(
                "name" to name,
                "host" to if (includeSensitive) address.hostString else "[REDACTED]",
                "port" to address.port,
                "players" to info.players.size,
            )
        }
        val backendPlayerCounts = plugin.proxy.servers.mapValues { (_, info) -> info.players.size }
        details["serverSummary"] = linkedMapOf(
            "serverCount" to backendPlayerCounts.size,
            "emptyServerCount" to backendPlayerCounts.count { it.value == 0 },
            "totalPlayersOnBackends" to backendPlayerCounts.values.sum(),
            "playersByServer" to backendPlayerCounts,
        )
        val playerPings = plugin.proxy.players.map { it.ping }.filter { it >= 0 }
        details["playerSummary"] = linkedMapOf(
            "onlineCount" to plugin.proxy.onlineCount,
            "playersByServer" to plugin.proxy.players
                .groupingBy { it.server?.info?.name ?: "[unassigned]" }
                .eachCount(),
            "pingMs" to linkedMapOf(
                "min" to playerPings.minOrNull(),
                "max" to playerPings.maxOrNull(),
                "average" to playerPings.average().takeIf { playerPings.isNotEmpty() },
                "buckets" to linkedMapOf(
                    "under50ms" to playerPings.count { it < 50 },
                    "50to99ms" to playerPings.count { it in 50..99 },
                    "100to199ms" to playerPings.count { it in 100..199 },
                    "200msOrMore" to playerPings.count { it >= 200 },
                ),
                "percentiles" to linkedMapOf(
                    "p50" to percentile(playerPings, 0.50),
                    "p95" to percentile(playerPings, 0.95),
                    "p99" to percentile(playerPings, 0.99),
                ),
            ),
        )
        if (includeSensitive) {
            details["players"] = plugin.proxy.players.map { player ->
                linkedMapOf<String, Any?>(
                    "username" to player.name,
                    "uuid" to player.uniqueId.toString(),
                    "ping" to player.ping,
                    "server" to player.server?.info?.name,
                )
            }
        } else {
            details["players"] = "redacted"
        }
        details["analytics"] = linkedMapOf(
            "status" to proxyAnalyticsStatus(details),
            "signals" to proxyAnalyticsSignals(details),
            "resources" to linkedMapOf(
                "onlinePlayers" to plugin.proxy.onlineCount,
                "backendServers" to plugin.proxy.servers.size,
                "emptyBackends" to backendPlayerCounts.count { it.value == 0 },
                "backendLoadMax" to backendPlayerCounts.values.maxOrNull(),
                "backendLoadMin" to backendPlayerCounts.values.minOrNull(),
                "backendLoadBuckets" to linkedMapOf(
                    "empty" to backendPlayerCounts.values.count { it == 0 },
                    "1to10" to backendPlayerCounts.values.count { it in 1..10 },
                    "11to50" to backendPlayerCounts.values.count { it in 11..50 },
                    "51OrMore" to backendPlayerCounts.values.count { it >= 51 },
                ),
                "registeredPlugins" to plugin.proxy.pluginManager.plugins.size,
            ),
            "counts" to linkedMapOf(
                "plugins" to plugin.proxy.pluginManager.plugins.size,
                "backends" to plugin.proxy.servers.size,
                "players" to plugin.proxy.onlineCount,
                "missingRequiredDependencies" to dependencyHealthCount(details),
                "highLatencyPlayers" to playerPings.count { it >= HIGH_PING_THRESHOLD },
                "duplicatePluginNames" to ((details["pluginSummary"] as? Map<*, *>)?.get("duplicateNames") as? Collection<*>)?.size,
                "unhealthyPlugins" to unhealthyPlugins,
            ),
            "playerDistribution" to linkedMapOf(
                "byServer" to (details["playerSummary"] as? Map<*, *>)?.get("playersByServer"),
                "pingBuckets" to ((details["playerSummary"] as? Map<*, *>)?.get("pingMs") as? Map<*, *>)?.get("buckets"),
            ),
        )
        details["coverage"] = linkedMapOf(
            "sections" to listOf(
                "platform",
                "plugins",
                "pluginDependencies",
                "pluginArtifacts",
                "pluginSummary",
                "pluginHealth",
                "servers",
                "serverSummary",
                "playerSummary",
                "players",
                "analytics",
                "playerSummary.pingPercentiles",
            ),
            "pluginCount" to plugin.proxy.pluginManager.plugins.size,
            "backendCount" to plugin.proxy.servers.size,
            "onlinePlayerCount" to plugin.proxy.onlineCount,
        )
        (details["collection"] as? MutableMap<String, Any?>)?.apply {
            this["durationMs"] = started.elapsedNow().inWholeMilliseconds
            this["pluginCount"] = plugin.proxy.pluginManager.plugins.size
            this["backendCount"] = plugin.proxy.servers.size
            this["onlinePlayerCount"] = plugin.proxy.onlineCount
        }
        return details
    }

    private fun proxyAnalyticsSignals(details: Map<String, Any?>): List<Map<String, Any?>> {
        val signals = mutableListOf<Map<String, Any?>>()
        if (dependencyHealthCount(details) > 0) {
            signals += linkedMapOf("code" to "missingRequiredDependencies", "severity" to "critical")
        }
        val duplicateNames = ((details["pluginSummary"] as? Map<*, *>)?.get("duplicateNames") as? Collection<*>)
        if (!duplicateNames.isNullOrEmpty()) {
            signals += linkedMapOf("code" to "duplicatePluginNames", "severity" to "warning", "count" to duplicateNames.size)
        }
        val unhealthy = (details["pluginHealth"] as? Collection<*>)?.count { (it as? Map<*, *>)?.get("status") != "healthy" } ?: 0
        if (unhealthy > 0) signals += linkedMapOf("code" to "unhealthyPlugins", "severity" to "warning", "count" to unhealthy)
        val summary = details["serverSummary"] as? Map<*, *>
        if ((summary?.get("emptyServerCount") as? Number)?.toInt()?.let { it > 0 } == true) {
            signals += linkedMapOf("code" to "emptyBackendServers", "severity" to "info")
        }
        val ping = ((details["playerSummary"] as? Map<*, *>)?.get("pingMs") as? Map<*, *>)?.get("average") as? Number
        if (ping != null && ping.toDouble() >= HIGH_PING_THRESHOLD) {
            signals += linkedMapOf("code" to "highPlayerLatency", "severity" to "warning", "averageMs" to ping)
        }
        val percentiles = ((details["playerSummary"] as? Map<*, *>)?.get("pingMs") as? Map<*, *>)?.get("percentiles") as? Map<*, *>
        val p95 = (percentiles?.get("p95") as? Number)?.toDouble()
        if (p95 != null && p95 >= HIGH_PING_THRESHOLD * 1.5) {
            signals += linkedMapOf("code" to "highLatencyTail", "severity" to "elevated", "p95Ms" to p95)
        }
        return signals
    }

    private fun proxyAnalyticsStatus(details: Map<String, Any?>): String = when {
        proxyAnalyticsSignals(details).any { it["severity"] == "critical" } -> "critical"
        proxyAnalyticsSignals(details).isNotEmpty() -> "attention"
        else -> "healthy"
    }

    private fun dependencyHealthCount(details: Map<String, Any?>): Int =
        (details["dependencyHealth"] as? Collection<*>)
            ?.count { (it as? Map<*, *>)?.get("healthy") == false }
            ?: 0

    private fun formatUtc(file: java.io.File): String =
        java.time.Instant.ofEpochMilli(file.lastModified()).toString()

    private fun percentile(values: List<Int>, percentile: Double): Int? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val index = ((sorted.size - 1) * percentile).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    private companion object {
        const val HIGH_PING_THRESHOLD = 200.0
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

    override fun remotePolicyContext(
        owner: Any,
        metadata: PluginMetadata,
        values: Map<String, String>,
    ): RemotePolicyContext {
        val target = owner as? Plugin
            ?: throw IllegalArgumentException(
                "Remote policy owner must be a BungeeCord plugin; " +
                    "received ${owner.javaClass.name}",
            )

        return BungeeRemotePolicyContextFactory.create(target, values)
    }

    override fun bind(library: PnLibrary) {
        check(!closed.get()) {
            "Bungee platform adapter is closed"
        }
        check(bound.compareAndSet(false, true)) {
            "Bungee platform adapter is already bound"
        }

        updateCommand = library.commands.register(
            plugin,
            ProxyUpdateCommand(library).definition(),
        )
    }

    override fun executeGlobal(task: Runnable) {
        if (closed.get()) return

        plugin.proxy.scheduler.runAsync(plugin, task)
    }

    override fun executeReply(recipient: Any, task: Runnable) {
        executeGlobal(task)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        observeNativeLogs(null)

        updateCommand?.close()
        updateCommand = null

        bound.set(false)
    }

    private fun Plugin.toSnapshot(): PluginSnapshot {
        val metadata = description

        return PluginSnapshot(
            id = metadata.name,
            name = metadata.name,
            version = metadata.version,
            authors = listOfNotNull(metadata.author),
            mainClass = metadata.main,
        )
    }
}
