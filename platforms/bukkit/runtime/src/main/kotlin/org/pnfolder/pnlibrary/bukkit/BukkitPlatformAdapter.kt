package org.pnfolder.pnlibrary.bukkit

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import org.pnfolder.pnlibrary.api.currency.currencyProviders
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.remote.RemotePolicyContext
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.bukkit.commands.BukkitCommandAdapter
import org.pnfolder.pnlibrary.bukkit.commands.BukkitCommandSender
import org.pnfolder.pnlibrary.bukkit.commands.BukkitControlCommand
import org.pnfolder.pnlibrary.bukkit.compat.ServerCapabilities
import org.pnfolder.pnlibrary.bukkit.remote.BukkitRemotePolicyContextFactory
import org.pnfolder.pnlibrary.bukkit.server.ServerInfo
import org.pnfolder.pnlibrary.bukkit.tasks.BukkitTaskAdapter
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.core.lifecycle.registerRuntimeCommand
import org.pnfolder.pnlibrary.spi.audiences.PlatformAudienceAdapter
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandAdapter
import org.pnfolder.pnlibrary.spi.metrics.PlatformMetricsFactory
import org.pnfolder.pnlibrary.spi.platform.JulLogObserver
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter
import org.pnfolder.pnlibrary.spi.platform.PlatformLifecycle
import org.pnfolder.pnlibrary.spi.platform.PlatformPlugins
import org.pnfolder.pnlibrary.spi.platform.PlatformSnapshot
import org.pnfolder.pnlibrary.spi.platform.PluginSnapshot
import org.pnfolder.pnlibrary.spi.platform.UncaughtExceptionLogObserver
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter
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
    private val plugins = PlatformPlugins<Plugin>(
        platformName = "Bukkit",
        installed = { Bukkit.getPluginManager().plugins.asList() },
        resolve = { owner -> owner as? Plugin },
    ) { installed ->
        val description = installed.description
        PluginSnapshot(
            id = installed.name,
            name = installed.name,
            version = description.version,
            authors = description.authors,
            mainClass = description.main,
        )
    }
    private val diagnosticsCollector = BukkitDiagnosticDetailsCollector()
    private val logObserver = JulLogObserver({ Bukkit.getLogger() }, plugin) { loggerName ->
        JulLogObserver.matchPlugin(loggerName, Bukkit.getPluginManager().plugins.asList()) { it.name }
    }
    private val uncaughtExceptionObserver = UncaughtExceptionLogObserver(plugin)
    private val schedulerBridge = BukkitSchedulerBridge(plugin, ::log)
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
        BukkitCommandAdapter(audienceService, plugin)

    override val audienceAdapter: PlatformAudienceAdapter =
        BukkitAudienceAdapter(audienceService)

    override val taskAdapter: PlatformTaskAdapter =
        BukkitTaskAdapter(plugin)

    /** Writes to the owning plugin's logger, falling back to pnLibrary's own. */
    override val logHandler: (Any, LogLevel, String, Throwable?) -> Unit =
        logObserver.logHandler { owner -> (owner as? Plugin)?.logger ?: plugin.logger }

    /** Sends legacy-formatted output to the server console. */
    override fun console(owner: Any, message: String) {
        Bukkit.getConsoleSender().sendMessage(message)
    }

    /** Reads native plugin metadata, warning and returning null for unsupported owners. */
    override fun ownerMetadata(owner: Any): PluginSnapshot? = plugins.metadata(owner, this)

    /** Returns installed plugin versions for compatibility checks. */
    override fun installedPlugins(): Map<String, String> = plugins.versions()

    /** Collects typed platform metadata without individual player identities. */
    override fun snapshot(): PlatformSnapshot =
        PlatformSnapshot(
            name = implementationName,
            version = Bukkit.getBukkitVersion(),
            onlinePlayers = Bukkit.getOnlinePlayers().size,
            plugins = plugins.snapshots(),
        )

    /** Creates a policy context for an installed native Bukkit plugin owner. */
    override fun remotePolicyContext(
        owner: Any,
        values: Map<String, String>,
    ): RemotePolicyContext =
        BukkitRemotePolicyContextFactory.create(plugins.requireInstalled(owner, "Remote policy owner"), values)

    /** Disables an installed native plugin through Bukkit's plugin manager. */
    override fun disableOwner(owner: Any): Boolean {
        Bukkit.getPluginManager().disablePlugin(plugins.requireInstalled(owner, "Disabled owner"))
        return true
    }

    /** Registers or removes the observer of server and plugin warnings, errors and uncaught thread exceptions. */
    @Synchronized
    override fun observeNativeLogs(
        observer: ((Any, LogLevel, String, Throwable?) -> Unit)?,
    ) {
        if (observer != null) lifecycle.checkOpen()
        logObserver.observe(observer)
        uncaughtExceptionObserver.observe(observer)
    }

    /** Registers commands and lifecycle listeners, rolling back partial initialization. */
    override fun bind(library: PnLibrary) {
        lifecycle.bind {
            lifecycleListener = lifecycle.track(BukkitLifecycleListener(plugin, library).start())
            lifecycle.track(library.registerRuntimeCommand(BukkitControlCommand(plugin, library, library.currencyProviders).definition()))
        }
    }

    /** Collects details on the appropriate server scheduler with a bounded wait. */
    override fun diagnosticDetails(
        includeSensitive: Boolean,
    ): Map<String, Any?> {
        if (!ServerCapabilities.isFolia && Bukkit.isPrimaryThread()) {
            return diagnosticsCollector.collect(snapshot(), includeSensitive)
        }

        val result = CompletableFuture<Map<String, Any?>>()
        executeGlobal(
            Runnable {
                runCatchingNonFatal {
                    diagnosticsCollector.collect(snapshot(), includeSensitive)
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
            schedulerBridge.executeGlobal(task)
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
            schedulerBridge.executeEntity(nativeRecipient, task)
            return
        }

        executeGlobal(task)
    }

    /** Releases registrations, cancels native tasks and stops native log observation once. */
    override fun close() {
        lifecycle.closeAdapter(null, taskAdapter) { observeNativeLogs(null) }
        lifecycleListener = null
    }

    private companion object {
        const val DIAGNOSTIC_TIMEOUT_SECONDS = 15L
    }
}
