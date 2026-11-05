package org.pnfolder.pnlibrary.core.runtime

import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.activity.ActivityService
import org.pnfolder.pnlibrary.api.audiences.AudienceService
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.CommandRegistration
import org.pnfolder.pnlibrary.api.config.ConfigurationService
import org.pnfolder.pnlibrary.api.diagnostics.DebugRequest
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticReport
import org.pnfolder.pnlibrary.api.events.EventService
import org.pnfolder.pnlibrary.api.expansions.ExpansionManager
import org.pnfolder.pnlibrary.api.logging.LoggingService
import org.pnfolder.pnlibrary.api.metrics.MetricsService
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapterRegistry
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderValueStore
import org.pnfolder.pnlibrary.api.platform.PlatformProvider
import org.pnfolder.pnlibrary.api.plugin.PluginRegistry
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.api.runtime.PnLibraryProvider
import org.pnfolder.pnlibrary.api.services.ServiceManager
import org.pnfolder.pnlibrary.api.tasks.TaskId
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.tasks.TaskSnapshot
import org.pnfolder.pnlibrary.api.updates.UpdateService
import org.pnfolder.pnlibrary.api.version.SemanticVersion
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.common.time.DisplayZone
import org.pnfolder.pnlibrary.core.cleanup.ResourceCleanup
import org.pnfolder.pnlibrary.core.commands.standardCommandFeedback
import org.pnfolder.pnlibrary.core.diagnostics.DiagnosticsRegistry
import org.pnfolder.pnlibrary.core.diagnostics.configFileDiagnostics
import org.pnfolder.pnlibrary.core.diagnostics.diagnosticCommand
import org.pnfolder.pnlibrary.core.events.EventDispatcher
import org.pnfolder.pnlibrary.core.events.EventHistory
import org.pnfolder.pnlibrary.core.journal.JournalDetail
import org.pnfolder.pnlibrary.core.journal.JournalGroup
import org.pnfolder.pnlibrary.core.lifecycle.RuntimeResources
import org.pnfolder.pnlibrary.core.lifecycle.registerRuntimeCommand
import org.pnfolder.pnlibrary.core.logging.ModuleLogging
import org.pnfolder.pnlibrary.core.metrics.MetricsSessions
import org.pnfolder.pnlibrary.core.observability.SupportRuntime
import org.pnfolder.pnlibrary.core.placeholders.LibraryPlaceholders
import org.pnfolder.pnlibrary.core.placeholders.placeholdersCommand
import org.pnfolder.pnlibrary.core.platform.NativePlatform
import org.pnfolder.pnlibrary.core.plugin.PluginRegistryImpl
import org.pnfolder.pnlibrary.core.plugin.SharedFeatures
import org.pnfolder.pnlibrary.core.remote.RemotePolicyMonitor
import org.pnfolder.pnlibrary.core.remote.RemotePolicyNoticeRenderer
import org.pnfolder.pnlibrary.core.runtime.details.RuntimeDiagnosticsAssembler
import org.pnfolder.pnlibrary.core.runtime.details.RuntimeFeatureSet
import org.pnfolder.pnlibrary.core.runtime.details.RuntimeJournal
import org.pnfolder.pnlibrary.core.runtime.details.RuntimeMetricsBootstrap
import org.pnfolder.pnlibrary.core.runtime.lifecycle.RuntimeShutdownPlan
import org.pnfolder.pnlibrary.core.runtime.platform.PlatformDataFolderResolver
import org.pnfolder.pnlibrary.core.runtime.platform.PlatformRegistration
import org.pnfolder.pnlibrary.core.runtime.platform.PlatformRuntimeContext
import org.pnfolder.pnlibrary.core.runtime.platform.PlatformServiceSet
import org.pnfolder.pnlibrary.core.stats.StatsRuntime
import org.pnfolder.pnlibrary.core.tasks.TaskControl
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandDispatcher
import java.nio.file.Path
import java.time.ZoneId
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Internal composition root: creates every runtime part in dependency order, then [init] starts them.
 * Platform modules depend on [PnLibrary] and must not cast the facade to this class.
 */
internal class PnLibraryImpl(
    override val owner: Any,
    private val platform: PlatformRuntimeContext,
    override val diagnostics: DiagnosticsRegistry,
    private val config: PnLibraryConfig = PnLibraryConfig(),
    private val onClose: () -> Unit = {},
) : PnLibrary, PlatformRegistration, RuntimeResources {
    private val closedFlag = AtomicBoolean(false)
    private val startedAtMillis = System.currentTimeMillis()

    override val configuration: PnLibraryConfig get() = config
    override val isClosed: Boolean get() = closedFlag.get()
    override val version: String = platform.ownership.ownerDetails(owner)["version"] ?: "unknown"
    val dataFolder: Path = PlatformDataFolderResolver.resolve(owner, platform.dataFolder)

    init {
        DisplayZone.zone = ZoneId.of(config.timeZone)
    }

    // Support and journal come first: every later part reports its failures through them.
    private val journalRuntime = RuntimeJournal(dataFolder, config, platform.ownership, startedAtMillis, EVENTS_COMMAND)
    private var libraryMetrics: PluginMetrics? = null
    private val support: SupportRuntime = SupportRuntime(
        owner = owner,
        ownership = platform.ownership,
        identity = platform.identity,
        platformDiagnostics = platform.diagnostics,
        platformLogObservation = platform.logObservation,
        platformLogging = platform.logging,
        diagnostics = diagnostics,
        config = config,
        dataFolder = dataFolder,
        runtimeDiagnostics = { runtimeDiagnostics.assemble() },
        journal = journalRuntime::record,
        onRuntimeError = ::reportRuntimeError,
    )
    private val metricsService = MetricsSessions(platform.metricsFactory, platform.identity.type)
    override val logging: LoggingService = ModuleLogging(
        platform.logging,
        platform.ownership,
        platform.diagnostics,
        platform.identity,
        support.logs,
        capture = support.capture,
    )

    private val features = RuntimeFeatureSet(platform, dataFolder, support.capture).also { set ->
        set.configurations.fileListener = configFileDiagnostics(diagnostics)
    }
    private val platformServices = PlatformServiceSet(
        commandAdapter = platform.commandAdapter,
        audienceAdapter = platform.audienceAdapter,
        taskAdapter = platform.taskAdapter,
        platformLogging = support.capture,
        taskHistoryCapacity = config.taskHistoryCapacity,
        taskHistoryFile = dataFolder.resolve("task-history.tsv"),
        errorLogger = support::recordRuntimeError,
        commandFeedback = standardCommandFeedback(config),
    )
    private val tasks = platformServices.tasks
    private val commands = platformServices.commands
    private val eventService = EventDispatcher(
        EventHistory(dataFolder.resolve("event-history.tsv"), config.eventHistoryDays, { DisplayZone.zone }),
    ) { pluginId, message, error -> support.recordRuntimeError(owner, "[$pluginId] $message", error) }
    private val platformProvider = NativePlatform()

    override val observability get() = support.service
    override val activity: ActivityService get() = support.service
    override val metrics: MetricsService get() = metricsService
    override val configurations: ConfigurationService get() = features.configurations
    override val updates: UpdateService get() = features.updates
    override val runtimeTasks: TaskScope get() = tasks.scope(owner)
    override val audiences: AudienceService get() = platformServices.audiences
    override val services: ServiceManager get() = features.services
    override val expansions: ExpansionManager get() = features.expansions
    override val events: EventService get() = eventService
    override val platforms: PlatformProvider get() = platformProvider
    override val placeholderAdapters: PlaceholderAdapterRegistry get() = features.placeholders
    override val placeholderValues: PlaceholderValueStore get() = features.placeholderValues

    override val plugins: PluginRegistry = PluginRegistryImpl(
        ownership = platform.ownership,
        identity = platform.identity,
        remotePolicies = RemotePolicyMonitor(platform.ownership, platform.execution, RemotePolicyNoticeRenderer(support.capture)),
        events = eventService,
        tasks = tasks,
        services = features.services,
        logging = logging,
        libraryVersion = version,
        configurations = features.configurations,
        metrics = metricsService,
        diagnostics = diagnostics,
        updates = features.updates,
        shared = SharedFeatures(features.placeholders, features.currency, features.expansions, features.libraryExpansions),
        commands = commands,
        directDownloads = features.downloads,
    )
    private val runtimeDiagnostics: RuntimeDiagnosticsAssembler = RuntimeDiagnosticsAssembler(
        version = version,
        isClosed = { isClosed },
        identity = platform.identity,
        dataFolder = dataFolder,
        config = config,
        tasks = tasks,
        updates = features.updates,
        diagnostics = diagnostics,
        support = support,
        metrics = metricsService,
        commands = commands,
        journal = journalRuntime::summary,
    )
    private val stats = StatsRuntime(::taskSnapshots, { events.inspector.events() }, journalRuntime.store)
    private val mountable = LinkedHashMap<String, CommandDefinition>()

    fun init() {
        support.initialize { isClosed }
        libraryMetrics = RuntimeMetricsBootstrap(
            registry = metricsService,
            platform = platform.identity,
            owner = owner,
            warning = { message -> logging.logger(owner, PnLibraryIdentity.NAME).warning(message) },
        ).start()
        stats.start()
        // The platform's `/pn` control command mounts these as its subcommands.
        mountable["debug"] = diagnosticCommand(this, "/pn debug")
        mountable["events"] = journalRuntime.command()
        mountable["stats"] = stats.command()
        mountable["placeholders"] = placeholdersCommand(features.placeholders)
        LibraryPlaceholders(features.placeholders, startedAtMillis, { plugins.all().size }, stats::values).register()
        registerRuntimeCommand(libraryInfoCommand(this, platform.identity) {
            platform.ownership.ownerDetails(owner)["version"] ?: "неизвестна"
        })
    }

    override fun close() {
        if (!closedFlag.compareAndSet(false, true)) return
        RuntimeShutdownPlan(
            plugins = plugins,
            commands = commands,
            audiences = platformServices.audiences,
            platforms = platformProvider,
            currency = features.currency,
            expansions = features.expansions,
            libraryExpansions = features.libraryExpansions,
            configurations = features.configurations,
            libraryMetrics = { libraryMetrics },
            metrics = metricsService,
            updates = features.updates,
            events = AutoCloseable(eventService::close),
            services = features.services,
            tasks = tasks,
            support = AutoCloseable { ResourceCleanup.closeAll(stats::close, support::close, journalRuntime::close) },
            releaseProvider = { PnLibraryProvider.clear(this) },
            closePlatform = platform.shutdown::close,
            onClose = onClose,
        ).run()
        libraryMetrics = null
    }

    override fun isAtLeastVersion(minimumVersion: String): Boolean =
        SemanticVersion.tryParse(version)?.isAtLeast(minimumVersion) ?: false

    override fun createDiagnosticReport(request: DebugRequest): DiagnosticReport {
        check(!isClosed) { "pnLibrary instance is closed" }
        return support.createDiagnosticReport(request)
    }

    override fun registerRuntimeCommand(command: CommandDefinition): CommandRegistration = commands.register(owner, command)

    override fun mountableCommand(name: String): CommandDefinition? = mountable[name]

    override fun dispatchCommand(command: CommandDefinition, context: CommandContext): CompletionStage<Void>? =
        (commands as? PlatformCommandDispatcher)?.execute(command, context)

    override fun <T : Any> registerPlatform(type: Class<T>, implementation: T): AutoCloseable =
        platformProvider.register(type, implementation)

    override fun taskSnapshots(): List<TaskSnapshot> = tasks.query().filterNot { it.hidden }

    override fun cancelTask(id: String): TaskControl =
        runCatchingNonFatal { tasks.cancelByAdmin(TaskId(id)) }.getOrDefault(TaskControl.UNAVAILABLE)

    override fun runTaskNow(id: String): TaskControl =
        runCatchingNonFatal { tasks.runNow(TaskId(id)) }.getOrDefault(TaskControl.UNAVAILABLE)

    override fun journalJoinNotice(): List<Component> = journalRuntime.queries.joinNotice()

    override fun journalGroups(level: String?, sinceHours: Int, limit: Int): List<JournalGroup> =
        journalRuntime.queries.groups(level, sinceHours, limit)

    override fun journalDetail(id: String): JournalDetail? = journalRuntime.queries.detail(id)

    override fun statsSnapshot() = stats.snapshot()

    override fun placeholderInfos() = features.placeholders.describe()

    private fun reportRuntimeError(logOwner: Any, error: Throwable) {
        libraryMetrics?.errorReporter?.capture(
            throwable = error,
            operation = "pnlibrary.runtime",
            attributes = mapOf("source" to logOwner.javaClass.name),
        )
    }

    private companion object {
        /** The journal lives under `/pn` on every platform; chat buttons and notices link to it. */
        const val EVENTS_COMMAND = "/pn events"
    }
}
