package ru.privatenull.pnlibrary.bukkit

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.api.commands.CommandRegistration
import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.api.plugin.PluginMetadata
import ru.privatenull.pnlibrary.api.remote.RemotePolicyContext
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.bukkit.commands.BukkitCommandAdapter
import ru.privatenull.pnlibrary.bukkit.commands.BukkitCommandSender
import ru.privatenull.pnlibrary.bukkit.commands.BukkitControlCommand
import ru.privatenull.pnlibrary.bukkit.compat.ServerCapabilities
import ru.privatenull.pnlibrary.bukkit.remote.BukkitRemotePolicyContextFactory
import ru.privatenull.pnlibrary.bukkit.server.ServerInfo
import ru.privatenull.pnlibrary.bukkit.tasks.BukkitTaskAdapter
import ru.privatenull.pnlibrary.spi.audiences.PlatformAudienceAdapter
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandAdapter
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.spi.platform.PlatformSnapshot
import ru.privatenull.pnlibrary.spi.platform.PluginSnapshot
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskAdapter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord

/**
 * Runtime adapter for Bukkit-compatible Minecraft servers.
 *
 * The adapter supports legacy Bukkit, Paper, and Folia scheduler models. It owns the native
 * command transport, lifecycle listener, diagnostic collector, and native-log observer.
 */
internal class BukkitPlatformAdapter(
    val plugin: Plugin,
    audienceService: BukkitAudienceService,
) : PlatformAdapter {

    private val closed = AtomicBoolean()
    private val bound = AtomicBoolean()
    private val ownLogCall = ThreadLocal.withInitial { false }
    private val diagnosticsCollector = BukkitDiagnosticsCollector()

    @Volatile
    private var nativeLogObserver: ((Any, LogLevel, String, Throwable?) -> Unit)? = null

    private var lifecycleListener: BukkitLifecycleListener? = null
    private var controlRegistration: CommandRegistration? = null

    private val nativeLogHandler = object : Handler() {
        override fun publish(record: LogRecord?) {
            if (record == null || ownLogCall.get()) return
            if (record.level.intValue() < Level.WARNING.intValue()) return

            val level = when {
                record.level.intValue() >= Level.SEVERE.intValue() -> LogLevel.ERROR
                else -> LogLevel.WARNING
            }
            val source = Bukkit.getPluginManager().plugins.firstOrNull { installedPlugin ->
                record.loggerName?.contains(installedPlugin.name, ignoreCase = true) == true
            } ?: plugin

            nativeLogObserver?.invoke(
                source,
                level,
                record.message ?: "Native platform error",
                record.thrown,
            )
        }

        override fun flush() = Unit

        override fun close() = Unit
    }

    override val type: PlatformType = PlatformType.BUKKIT
    override val dataFolder = plugin.dataFolder.toPath()

    override val implementationName: String
        get() = Bukkit.getName().ifBlank { type.displayName }

    val serverInfo: ServerInfo by lazy {
        ServerInfo(
            name = implementationName,
            version = Bukkit.getVersion(),
            minecraftVersion = ServerCapabilities.minecraftVersion,
            rawMinecraftVersion = ServerCapabilities.rawMinecraftVersion,
        )
    }

    override val metricsFactory: PlatformMetricsFactory =
        BukkitMetricsFactory()

    override val commandAdapter: PlatformCommandAdapter =
        BukkitCommandAdapter(plugin, audienceService)

    override val audienceAdapter: PlatformAudienceAdapter =
        BukkitAudienceAdapter(audienceService)

    override val taskAdapter: PlatformTaskAdapter =
        BukkitTaskAdapter(plugin)

    override val logHandler: (Any, LogLevel, String, Throwable?) -> Unit =
        { owner, level, message, error ->
            val logger = (owner as? Plugin)?.logger ?: plugin.logger
            val nativeLevel = when (level) {
                LogLevel.WARNING -> Level.WARNING
                LogLevel.ERROR -> Level.SEVERE
                else -> Level.INFO
            }

            ownLogCall.set(true)
            try {
                logger.log(nativeLevel, message, error)
            } finally {
                ownLogCall.set(false)
            }
        }

    override fun console(owner: Any, message: String) {
        Bukkit.getConsoleSender().sendMessage(message)
    }

    override fun ownerMetadata(owner: Any): PluginSnapshot? {
        val target = owner as? Plugin
            ?: return unsupportedOwner(owner)

        return target.toSnapshot()
    }

    override fun installedPlugins(): Map<String, String> =
        Bukkit.getPluginManager().plugins.associate { installedPlugin ->
            installedPlugin.name to installedPlugin.description.version
        }

    override fun snapshot(): PlatformSnapshot =
        PlatformSnapshot(
            name = implementationName,
            version = Bukkit.getBukkitVersion(),
            onlinePlayers = Bukkit.getOnlinePlayers().size,
            plugins = Bukkit.getPluginManager().plugins.map { it.toSnapshot() },
        )

    override fun remotePolicyContext(
        owner: Any,
        metadata: PluginMetadata,
        values: Map<String, String>,
    ): RemotePolicyContext =
        BukkitRemotePolicyContextFactory.create(owner as Plugin, values)

    override fun disableOwner(owner: Any): Boolean {
        Bukkit.getPluginManager().disablePlugin(owner as Plugin)
        return true
    }

    @Synchronized
    override fun observeNativeLogs(
        observer: ((Any, LogLevel, String, Throwable?) -> Unit)?,
    ) {
        if (nativeLogObserver == null && observer != null) {
            Bukkit.getLogger().addHandler(nativeLogHandler)
        }
        if (nativeLogObserver != null && observer == null) {
            Bukkit.getLogger().removeHandler(nativeLogHandler)
        }

        nativeLogObserver = observer
    }

    override fun bind(library: PnLibrary) {
        check(!closed.get()) {
            "Bukkit platform adapter is closed"
        }
        check(bound.compareAndSet(false, true)) {
            "Bukkit platform adapter is already bound"
        }

        try {
            lifecycleListener = BukkitLifecycleListener(plugin, library).start()
            controlRegistration = library.commands.register(
                plugin,
                BukkitControlCommand(plugin, library).definition(),
            )
        } catch (error: Throwable) {
            releaseBindings()
            bound.set(false)
            throw error
        }
    }

    override fun diagnosticDetails(
        includeSensitive: Boolean,
    ): Map<String, Any?> {
        if (!ServerCapabilities.isFolia && Bukkit.isPrimaryThread()) {
            return diagnosticsCollector.collect(includeSensitive)
        }

        val result = CompletableFuture<Map<String, Any?>>()
        executeGlobal(
            Runnable {
                runCatching {
                    diagnosticsCollector.collect(includeSensitive)
                }.onSuccess(result::complete)
                    .onFailure(result::completeExceptionally)
            },
        )

        return result.get(DIAGNOSTIC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    }

    override fun executeGlobal(task: Runnable) {
        if (closed.get()) return

        if (ServerCapabilities.isFolia) {
            executeFoliaGlobal(task)
            return
        }

        if (Bukkit.isPrimaryThread()) {
            task.run()
        } else {
            Bukkit.getScheduler().runTask(plugin, task)
        }
    }

    override fun whenServerReady(task: Runnable) {
        lifecycleListener?.whenServerReady(task) ?: executeGlobal(task)
    }

    override fun executeReply(recipient: Any, task: Runnable) {
        if (closed.get()) return

        val nativeRecipient = (recipient as? BukkitCommandSender)?.native ?: recipient
        if (nativeRecipient is Player && ServerCapabilities.isFolia) {
            executeFoliaEntity(nativeRecipient, task)
            return
        }

        executeGlobal(task)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return

        releaseBindings()
        observeNativeLogs(null)
        bound.set(false)
    }

    private fun executeFoliaGlobal(task: Runnable) {
        try {
            val scheduler = Bukkit::class.java
                .getMethod("getGlobalRegionScheduler")
                .invoke(null)
            val run = scheduler.javaClass.getMethod(
                "run",
                Plugin::class.java,
                java.util.function.Consumer::class.java,
            )

            run.invoke(
                scheduler,
                plugin,
                java.util.function.Consumer<Any> { task.run() },
            )
        } catch (error: ReflectiveOperationException) {
            log(
                plugin,
                LogLevel.ERROR,
                "Не удалось передать задачу Folia GlobalRegionScheduler",
                error,
            )
        }
    }

    private fun executeFoliaEntity(
        player: Player,
        task: Runnable,
    ) {
        try {
            val scheduler = player.javaClass
                .getMethod("getScheduler")
                .invoke(player)
            val run = scheduler.javaClass.getMethod(
                "run",
                Plugin::class.java,
                java.util.function.Consumer::class.java,
                Runnable::class.java,
            )

            run.invoke(
                scheduler,
                plugin,
                java.util.function.Consumer<Any> { task.run() },
                null,
            )
        } catch (error: Exception) {
            log(
                plugin,
                LogLevel.ERROR,
                "Не удалось передать задачу Folia EntityScheduler",
                error,
            )
        }
    }

    private fun releaseBindings() {
        controlRegistration?.close()
        controlRegistration = null

        lifecycleListener?.close()
        lifecycleListener = null
    }

    private fun Plugin.toSnapshot(): PluginSnapshot =
        PluginSnapshot(
            id = name,
            name = name,
            version = description.version,
            authors = description.authors,
        )

    private companion object {
        const val DIAGNOSTIC_TIMEOUT_SECONDS = 15L
    }
}
