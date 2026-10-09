package ru.privatenull.pnlibrary.velocity

import com.velocitypowered.api.plugin.PluginDescription
import com.velocitypowered.api.proxy.ProxyServer
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.slf4j.Logger
import ru.privatenull.pnlibrary.api.commands.CommandRegistration
import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.plugin.PluginMetadata
import ru.privatenull.pnlibrary.api.remote.RemotePolicyContext
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.core.updates.ProxyUpdateCommand
import ru.privatenull.pnlibrary.spi.audiences.PlatformAudienceAdapter
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandAdapter
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.spi.platform.PlatformSnapshot
import ru.privatenull.pnlibrary.spi.platform.PluginSnapshot
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskAdapter
import ru.privatenull.pnlibrary.velocity.commands.VelocityCommandAdapter
import ru.privatenull.pnlibrary.velocity.tasks.VelocityTaskAdapter
import java.nio.file.Path
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.TimeSource

/**
 * Runtime adapter for Velocity proxy servers.
 *
 * Velocity does not expose an entity-region scheduler. Global and recipient work therefore use
 * the native plugin scheduler owned by [plugin].
 */
internal class VelocityPlatformAdapter(
    val plugin: Any,
    val server: ProxyServer,
    override val metricsFactory: PlatformMetricsFactory,
    override val dataFolder: Path,
    private val logger: Logger,
) : PlatformAdapter {

    private val closed = AtomicBoolean()
    private val bound = AtomicBoolean()

    private var updateCommand: CommandRegistration? = null
    private var previousUncaughtHandler: Thread.UncaughtExceptionHandler? = null
    private var installedUncaughtHandler: Thread.UncaughtExceptionHandler? = null

    override val type: PlatformType = PlatformType.VELOCITY

    override val implementationName: String
        get() = server.version.name.ifBlank { type.displayName }

    override val commandAdapter: PlatformCommandAdapter =
        VelocityCommandAdapter(plugin, server)

    override val audienceAdapter: PlatformAudienceAdapter =
        VelocityAudienceAdapter(server)

    override val taskAdapter: PlatformTaskAdapter =
        VelocityTaskAdapter(plugin, server)

    override val logHandler: (Any, LogLevel, String, Throwable?) -> Unit =
        { _, level, message, error ->
            when (level) {
                LogLevel.WARNING -> logger.warn(message, error)
                LogLevel.ERROR -> logger.error(message, error)
                else -> logger.info(message, error)
            }
        }

    override fun console(owner: Any, message: String) {
        server.consoleCommandSource.sendMessage(
            LEGACY_SERIALIZER.deserialize(message),
        )
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

    override fun ownerMetadata(owner: Any): PluginSnapshot? {
        val description = server.pluginManager.plugins
            .firstOrNull { it.instance.orElse(null) === owner }
            ?.description
            ?: return unsupportedOwner(owner)

        return description.toSnapshot()
    }

    override fun installedPlugins(): Map<String, String> =
        server.pluginManager.plugins.associate { installedPlugin ->
            installedPlugin.description.id to
                installedPlugin.description.version.orElse("unknown")
        }

    override fun snapshot(): PlatformSnapshot {
        val platformVersion = server.version

        return PlatformSnapshot(
            name = platformVersion.name,
            version = platformVersion.version,
            vendor = platformVersion.vendor,
            onlinePlayers = server.playerCount,
            registeredServers = server.allServers.map { it.serverInfo.name },
            plugins = server.pluginManager.plugins.map { it.description.toSnapshot() },
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
        details["pluginDependencies"] = server.pluginManager.plugins.associate { container ->
            container.description.id to container.description.dependencies.map { dependency ->
                linkedMapOf<String, Any?>(
                    "id" to dependency.id,
                    "version" to dependency.version.orElse(null),
                    "optional" to dependency.isOptional,
                )
            }
        }
        val installedPluginIds = server.pluginManager.plugins.map { it.description.id }.toSet()
        details["dependencyHealth"] = server.pluginManager.plugins.map { container ->
            val required = container.description.dependencies.filterNot { it.isOptional }
            val optional = container.description.dependencies.filter { it.isOptional }
            val missingRequired = required.map { it.id }.filterNot(installedPluginIds::contains)
            val missingOptional = optional.map { it.id }.filterNot(installedPluginIds::contains)
            linkedMapOf<String, Any?>(
                "plugin" to container.description.id,
                "missingRequired" to missingRequired,
                "missingOptional" to missingOptional,
                "healthy" to missingRequired.isEmpty(),
            )
        }
        details["pluginArtifacts"] = server.pluginManager.plugins.map { container ->
            val source = container.description.source.orElse(null)?.toFile()
            linkedMapOf<String, Any?>(
                "id" to container.description.id,
                "path" to if (includeSensitive) source?.absolutePath else "[REDACTED]",
                "sizeBytes" to source?.takeIf { it.isFile }?.length(),
                "lastModifiedUtc" to source?.takeIf { it.isFile }?.let {
                    java.time.Instant.ofEpochMilli(it.lastModified()).toString()
                },
                "sha256" to source?.takeIf { it.isFile }?.let(::sha256),
            )
        }
        details["pluginSummary"] = linkedMapOf(
            "total" to server.pluginManager.plugins.size,
            "versions" to server.pluginManager.plugins.associate {
                it.description.id to it.description.version.orElse("unknown")
            },
            "duplicateIds" to server.pluginManager.plugins
                .groupingBy { it.description.id.lowercase() }
                .eachCount()
                .filterValues { it > 1 }
                .keys
                .sorted(),
            "loadOrder" to server.pluginManager.plugins.map { it.description.id },
        )
        details["pluginHealth"] = server.pluginManager.plugins.map { container ->
            val metadata = container.description
            val health = (details["dependencyHealth"] as? Collection<*>)
                ?.filterIsInstance<Map<*, *>>()
                ?.firstOrNull { it["plugin"] == metadata.id }
            val missing = health?.get("missingRequired") as? Collection<*>
            linkedMapOf<String, Any?>(
                "id" to metadata.id,
                "version" to metadata.version.orElse("unknown"),
                "status" to if (missing.isNullOrEmpty()) "healthy" else "missingDependencies",
                "missingRequired" to missing.orEmpty(),
                "optionalDependencies" to metadata.dependencies.count { it.isOptional },
            )
        }
        details["servers"] = server.allServers.map { connection ->
            val address = connection.serverInfo.address
            linkedMapOf<String, Any?>(
                "name" to connection.serverInfo.name,
                "host" to if (includeSensitive) address.hostString else "[REDACTED]",
                "port" to address.port,
                "players" to connection.playersConnected.size,
            )
        }
        val backendPlayerCounts = server.allServers.associate { connection ->
            connection.serverInfo.name to connection.playersConnected.size
        }
        details["serverSummary"] = linkedMapOf(
            "serverCount" to backendPlayerCounts.size,
            "emptyServerCount" to backendPlayerCounts.count { it.value == 0 },
            "totalPlayersOnBackends" to backendPlayerCounts.values.sum(),
            "playersByServer" to backendPlayerCounts,
        )
        val playerPings = server.allPlayers.map { it.ping }.filter { it >= 0 }
        details["playerSummary"] = linkedMapOf(
            "onlineCount" to server.playerCount,
            "playersByServer" to server.allPlayers
                .groupingBy { it.currentServer.map { connection -> connection.serverInfo.name }.orElse("[unassigned]") }
                .eachCount(),
            "pingMs" to linkedMapOf(
                "min" to playerPings.minOrNull(),
                "max" to playerPings.maxOrNull(),
                "average" to playerPings.average().takeIf { playerPings.isNotEmpty() },
                "percentiles" to linkedMapOf(
                    "p50" to percentile(playerPings, 0.50),
                    "p95" to percentile(playerPings, 0.95),
                    "p99" to percentile(playerPings, 0.99),
                ),
            ),
        )
        if (includeSensitive) {
            details["players"] = server.allPlayers.map { player ->
                linkedMapOf<String, Any?>(
                    "username" to player.username,
                    "uuid" to player.uniqueId.toString(),
                    "ping" to player.ping,
                    "server" to player.currentServer.map { it.serverInfo.name }.orElse(null),
                )
            }
        } else {
            details["players"] = "redacted"
        }
        details["analytics"] = linkedMapOf(
            "status" to proxyAnalyticsStatus(details),
            "signals" to proxyAnalyticsSignals(details),
            "resources" to linkedMapOf(
                "onlinePlayers" to server.playerCount,
                "backendServers" to server.allServers.size,
                "emptyBackends" to backendPlayerCounts.count { it.value == 0 },
                "backendLoadMax" to backendPlayerCounts.values.maxOrNull(),
                "backendLoadMin" to backendPlayerCounts.values.minOrNull(),
                "registeredPlugins" to server.pluginManager.plugins.size,
            ),
            "counts" to linkedMapOf(
                "plugins" to server.pluginManager.plugins.size,
                "backends" to server.allServers.size,
                "players" to server.playerCount,
                "missingRequiredDependencies" to dependencyHealthCount(details),
                "highLatencyPlayers" to playerPings.count { it >= HIGH_PING_THRESHOLD },
                "duplicatePluginIds" to ((details["pluginSummary"] as? Map<*, *>)?.get("duplicateIds") as? Collection<*>)?.size,
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
            "pluginCount" to server.pluginManager.plugins.size,
            "backendCount" to server.allServers.size,
            "onlinePlayerCount" to server.playerCount,
        )
        (details["collection"] as? MutableMap<String, Any?>)?.apply {
            this["durationMs"] = started.elapsedNow().inWholeMilliseconds
            this["pluginCount"] = server.pluginManager.plugins.size
            this["backendCount"] = server.allServers.size
            this["onlinePlayerCount"] = server.playerCount
        }
        return details
    }

    private fun proxyAnalyticsSignals(details: Map<String, Any?>): List<Map<String, Any?>> {
        val signals = mutableListOf<Map<String, Any?>>()
        if (dependencyHealthCount(details) > 0) {
            signals += linkedMapOf("code" to "missingRequiredDependencies", "severity" to "critical")
        }
        val duplicateIds = ((details["pluginSummary"] as? Map<*, *>)?.get("duplicateIds") as? Collection<*>)
        if (!duplicateIds.isNullOrEmpty()) {
            signals += linkedMapOf("code" to "duplicatePluginIds", "severity" to "warning", "count" to duplicateIds.size)
        }
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

    override fun remotePolicyContext(
        owner: Any,
        metadata: PluginMetadata,
        values: Map<String, String>,
    ): RemotePolicyContext =
        VelocityRemotePolicyContextFactory.create(owner, server, values)

    override fun bind(library: PnLibrary) {
        check(!closed.get()) {
            "Velocity platform adapter is closed"
        }
        check(bound.compareAndSet(false, true)) {
            "Velocity platform adapter is already bound"
        }

        updateCommand = library.commands.register(
            plugin,
            ProxyUpdateCommand(library).definition(),
        )
    }

    override fun executeGlobal(task: Runnable) {
        if (closed.get()) return

        server.scheduler.buildTask(plugin, task).schedule()
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

    private fun percentile(values: List<Long>, percentile: Double): Long? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val index = ((sorted.size - 1) * percentile).toInt().coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }

    private fun PluginDescription.toSnapshot(): PluginSnapshot =
        PluginSnapshot(
            id = id,
            name = name.orElse(id),
            version = version.orElse("unknown"),
            authors = authors,
        )

    private companion object {
        const val HIGH_PING_THRESHOLD = 200.0
        val LEGACY_SERIALIZER: LegacyComponentSerializer =
            LegacyComponentSerializer.legacySection()
    }
}
