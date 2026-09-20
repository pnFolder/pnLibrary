package ru.privatenull.pnlibrary.bukkit

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.platform.PlatformType
import ru.privatenull.pnlibrary.bukkit.server.ServerInfo
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.bukkit.compat.ServerCapabilities
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandAdapter
import ru.privatenull.pnlibrary.spi.audiences.PlatformAudienceAdapter
import ru.privatenull.pnlibrary.api.commands.CommandRegistration
import ru.privatenull.pnlibrary.bukkit.commands.BukkitCommandAdapter
import ru.privatenull.pnlibrary.bukkit.commands.BukkitCommandSender
import ru.privatenull.pnlibrary.bukkit.commands.BukkitControlCommand
import ru.privatenull.pnlibrary.bukkit.tasks.BukkitTaskAdapter
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskAdapter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Level
import java.util.logging.Handler
import java.util.logging.LogRecord

/**
 * Runtime adapter for Bukkit-compatible Minecraft servers.
 *
 * The adapter supports legacy Bukkit as well as Paper and Folia scheduler models. It owns the
 * native command transport, lifecycle listeners, and the optional native-log observer;
 * [close] removes all of them idempotently. Consumer plugins should use the public pnLibrary API
 * instead of constructing this runtime component.
 *
 * @property plugin native plugin that owns scheduler and listener registrations
 */
internal class BukkitPlatformAdapter constructor(
    val plugin: Plugin,
    audienceService: BukkitAudienceService,
) : PlatformAdapter {

    private val closedFlag = AtomicBoolean(false)
    private val bound = AtomicBoolean(false)
    @Volatile
    private var nativeLogObserver: ((Any, LogLevel, String, Throwable?) -> Unit)? = null
    private var lifecycleListener: BukkitLifecycleListener? = null
    private var controlRegistration: CommandRegistration? = null
    private val ownLogCall = ThreadLocal.withInitial { false }
    private val diagnosticsCollector = BukkitDiagnosticsCollector()
    private val nativeLogHandler = object : Handler() {
        override fun publish(record: LogRecord?) {
            if (record == null || ownLogCall.get() || record.level.intValue() < Level.WARNING.intValue()) return
            val level = if (record.level.intValue() >= Level.SEVERE.intValue()) LogLevel.ERROR else LogLevel.WARNING
            val source = Bukkit.getPluginManager().plugins.firstOrNull {
                record.loggerName?.contains(it.name, ignoreCase = true) == true
            } ?: plugin
            nativeLogObserver?.invoke(source, level, record.message ?: "Native platform error", record.thrown)
        }
        override fun flush() = Unit
        override fun close() = Unit
    }

    override val type = PlatformType.BUKKIT
    override val implementationName: String get() = Bukkit.getName().ifBlank { type.displayName }
    val serverInfo: ServerInfo by lazy {
        ServerInfo(
            name = implementationName,
            version = Bukkit.getVersion(),
            minecraftVersion = ServerCapabilities.minecraftVersion,
            rawMinecraftVersion = ServerCapabilities.rawMinecraftVersion,
        )
    }
    override val metricsFactory: PlatformMetricsFactory = BukkitMetricsFactory()
    override val audienceAdapter: PlatformAudienceAdapter = BukkitAudienceAdapter(audienceService)
    override val commandAdapter: PlatformCommandAdapter = BukkitCommandAdapter(plugin, audienceService)
    override val taskAdapter: PlatformTaskAdapter = BukkitTaskAdapter(plugin)
    override val dataFolder = plugin.dataFolder.toPath()

    override fun log(owner: Any, level: LogLevel, message: String, error: Throwable?) {
        val target = (owner as? Plugin)?.logger ?: plugin.logger
        val nativeLevel = when (level) {
            LogLevel.WARNING -> Level.WARNING
            LogLevel.ERROR -> Level.SEVERE
            else -> Level.INFO
        }
        ownLogCall.set(true)
        try {
            if (error == null) target.log(nativeLevel, message) else target.log(nativeLevel, message, error)
        } finally {
            ownLogCall.set(false)
        }
    }

    @Synchronized
    override fun observeNativeLogs(observer: ((Any, LogLevel, String, Throwable?) -> Unit)?) {
        if (nativeLogObserver == null && observer != null) {
            Bukkit.getLogger().addHandler(nativeLogHandler)
        }
        if (nativeLogObserver != null && observer == null) {
            Bukkit.getLogger().removeHandler(nativeLogHandler)
        }
        nativeLogObserver = observer
    }

    override fun console(owner: Any, message: String) {
        Bukkit.getConsoleSender().sendMessage(message)
    }

    override fun ownerDetails(owner: Any): Map<String, String> {
        val target = owner as? Plugin ?: return emptyMap()
        return linkedMapOf(
            "id" to target.name,
            "name" to target.name,
            "version" to target.description.version,
            "authors" to target.description.authors.joinToString(", ").ifBlank { "pnFolder" },
        )
    }

    override fun installedPlugins(): Map<String, String> = Bukkit.getPluginManager().plugins.associate {
        it.name to it.description.version
    }

    override fun bind(library: PnLibrary) {
        check(!closedFlag.get()) { "Bukkit platform adapter is closed" }
        check(bound.compareAndSet(false, true)) { "Bukkit platform adapter is already bound" }
        try {
            lifecycleListener = BukkitLifecycleListener(plugin, library).start()
            controlRegistration = library.commands.register(
                plugin,
                BukkitControlCommand(plugin, library).definition(),
            )
        } catch (error: Throwable) {
            controlRegistration?.close()
            controlRegistration = null
            lifecycleListener?.close()
            lifecycleListener = null
            bound.set(false)
            throw error
        }
    }
    override fun details(): Map<String, Any?> = diagnosticDetails(includeSensitive = false)

    override fun diagnosticDetails(includeSensitive: Boolean): Map<String, Any?> {
        if (!ServerCapabilities.isFolia && Bukkit.isPrimaryThread()) {
            return diagnosticsCollector.collect(includeSensitive)
        }
        val snapshot = CompletableFuture<Map<String, Any?>>()
        executeGlobal(Runnable {
            runCatching { diagnosticsCollector.collect(includeSensitive) }
                .onSuccess(snapshot::complete)
                .onFailure(snapshot::completeExceptionally)
        })
        return snapshot.get(15, TimeUnit.SECONDS)
    }

    override fun executeGlobal(task: Runnable) {
        if (closedFlag.get()) return
        if (ServerCapabilities.isFolia) {
            try {
                val scheduler = Bukkit::class.java.getMethod("getGlobalRegionScheduler").invoke(null)
                val run = scheduler.javaClass.getMethod(
                    "run", Plugin::class.java, java.util.function.Consumer::class.java)
                run.invoke(scheduler, plugin, java.util.function.Consumer<Any> { task.run() })
                return
            } catch (error: ReflectiveOperationException) {
                log(plugin, LogLevel.ERROR, "Не удалось передать задачу Folia GlobalRegionScheduler", error)
                return
            }
        }
        if (Bukkit.isPrimaryThread()) {
            task.run()
        } else {
            Bukkit.getScheduler().runTask(plugin, task)
        }
    }

    override fun executeReply(recipient: Any, task: Runnable) {
        if (closedFlag.get()) return
        val nativeRecipient = (recipient as? BukkitCommandSender)?.native ?: recipient
        if (nativeRecipient is Player && ServerCapabilities.isFolia) {
            try {
                val getScheduler = nativeRecipient.javaClass.getMethod("getScheduler")
                val taskScheduler = getScheduler.invoke(nativeRecipient)
                val runMethod = taskScheduler.javaClass.getMethod(
                    "run",
                    Plugin::class.java,
                    java.util.function.Consumer::class.java,
                    Runnable::class.java,
                )
                runMethod.invoke(taskScheduler, plugin, java.util.function.Consumer<Any> { task.run() }, null)
                return
            } catch (error: Exception) {
                log(plugin, LogLevel.ERROR, "Не удалось передать задачу Folia EntityScheduler", error)
                return
            }
        }
        executeGlobal(task)
    }


    override fun close() {
        if (closedFlag.compareAndSet(false, true)) {
            controlRegistration?.close()
            controlRegistration = null
            lifecycleListener?.close()
            lifecycleListener = null
            observeNativeLogs(null)
            bound.set(false)
        }
    }
}
