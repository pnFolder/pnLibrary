package ru.privatenull.pnlibrary.bukkit

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
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
import ru.privatenull.pnlibrary.spi.platform.PlatformLifecycle
import ru.privatenull.pnlibrary.spi.platform.unsupportedOwner
import ru.privatenull.pnlibrary.spi.platform.PlatformSnapshot
import ru.privatenull.pnlibrary.spi.platform.PluginSnapshot
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskAdapter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

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

    private val lifecycle = PlatformLifecycle()
    private val diagnosticsCollector = BukkitDiagnosticsCollector()
    private val nativeLogBridge = BukkitNativeLogBridge(plugin)
    private var lifecycleListener: BukkitLifecycleListener? = null

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

    override val logHandler: (Any, LogLevel, String, Throwable?) -> Unit = nativeLogBridge.logHandler

    /** Sends legacy-formatted output to the server console. */
    override fun console(owner: Any, message: String) {
        Bukkit.getConsoleSender().sendMessage(message)
    }

    /** Reads native plugin metadata, warning and returning null for unsupported owners. */
    override fun ownerMetadata(owner: Any): PluginSnapshot? {
        val target = owner as? Plugin
            ?: return unsupportedOwner(owner, this)

        return target.toSnapshot()
    }

    /** Returns installed plugin versions for compatibility checks. */
    override fun installedPlugins(): Map<String, String> =
        Bukkit.getPluginManager().plugins.associate { installedPlugin ->
            installedPlugin.name to installedPlugin.description.version
        }

    /** Collects typed platform metadata without individual player identities. */
    override fun snapshot(): PlatformSnapshot =
        PlatformSnapshot(
            name = implementationName,
            version = Bukkit.getBukkitVersion(),
            onlinePlayers = Bukkit.getOnlinePlayers().size,
            plugins = Bukkit.getPluginManager().plugins.map { it.toSnapshot() },
        )

    /** Creates a policy context for the native Bukkit plugin owner. */
    override fun remotePolicyContext(
        owner: Any,
        metadata: PluginMetadata,
        values: Map<String, String>,
    ): RemotePolicyContext =
        BukkitRemotePolicyContextFactory.create(owner as Plugin, values)

    /** Disables a native plugin through Bukkit's plugin manager. */
    override fun disableOwner(owner: Any): Boolean {
        Bukkit.getPluginManager().disablePlugin(owner as Plugin)
        return true
    }

    /** Registers or removes the native warning and error observer. */
    @Synchronized
    override fun observeNativeLogs(
        observer: ((Any, LogLevel, String, Throwable?) -> Unit)?,
    ) {
        nativeLogBridge.observe(observer)
    }

    /** Registers commands and lifecycle listeners, rolling back partial initialization. */
    override fun bind(library: PnLibrary) {
        lifecycle.bind {
            lifecycleListener = lifecycle.track(BukkitLifecycleListener(plugin, library).start())
            lifecycle.track(library.commands.register(
                plugin,
                BukkitControlCommand(plugin, library).definition(),
            ))
        }
    }

    /** Collects details on the appropriate server scheduler with a bounded wait. */
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

    /** Runs work on the primary thread or Folia global-region scheduler. */
    override fun executeGlobal(task: Runnable) {
        if (lifecycle.isClosed) return

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

    /** Runs work after server readiness, falling back to global dispatch. */
    override fun whenServerReady(task: Runnable) {
        lifecycleListener?.whenServerReady(task) ?: executeGlobal(task)
    }

    /** Routes player replies to their Folia region, otherwise to global dispatch. */
    override fun executeReply(recipient: Any, task: Runnable) {
        if (lifecycle.isClosed) return

        val nativeRecipient = (recipient as? BukkitCommandSender)?.native ?: recipient
        if (nativeRecipient is Player && ServerCapabilities.isFolia) {
            executeFoliaEntity(nativeRecipient, task)
            return
        }

        executeGlobal(task)
    }

    override fun close() {
        if (lifecycle.isClosed) return
        try {
            observeNativeLogs(null)
        } finally {
            lifecycle.close()
            lifecycleListener = null
        }
    }

    /** Dispatches global work without linking optional Folia classes. */
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

    /** Dispatches player work to its owning Folia region. */
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

    /** Converts native plugin metadata to the shared snapshot contract. */
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
