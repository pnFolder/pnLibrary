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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Composes all service implementations into one [PnLibrary] runtime.
 *
 * This is the internal composition root for diagnostics, events, logging,
 * metrics, updates, and tasks. Platform modules depend on [PnLibrary] and must
 * not cast the facade to this implementation.
 */
internal class PnLibraryImpl(
    override val owner: Any,
    private val platform: PlatformRuntimeContext,
    override val diagnostics: DiagnosticsRegistry,
    val config: PnLibraryConfig = PnLibraryConfig(),
    private val onClose: () -> Unit = {},
) : PnLibrary, PlatformRegistration, RuntimeResources {

    // Keep the service wiring readable while the platform boundary remains one context object.
    private val platformIdentity get() = platform.identity
    private val platformOwnership get() = platform.ownership
    private val platformExecution get() = platform.execution
    private val platformLogging get() = platform.logging
    private val platformDiagnostics get() = platform.diagnostics
    private val platformLogObservation get() = platform.logObservation
    private val metricsFactory get() = platform.metricsFactory
    private val commandAdapter get() = platform.commandAdapter
    private val audienceAdapter get() = platform.audienceAdapter
    private val taskAdapter get() = platform.taskAdapter
    private val dataFolderHint get() = platform.dataFolder
    private val closePlatform get() = platform.shutdown::close

    override val configuration: PnLibraryConfig get() = config

    override val version: String = platformOwnership.ownerDetails(owner)["version"] ?: "unknown"
    override fun isAtLeastVersion(minimumVersion: String): Boolean =
        SemanticVersion.tryParse(version)?.isAtLeast(minimumVersion) ?: false

    private val closedFlag = AtomicBoolean(false)
    override val isClosed: Boolean get() = closedFlag.get()
    private val metricsService = MetricsSessions(metricsFactory, platform.identity.type)
    private var libraryMetrics: PluginMetrics? = null
    val dataFolder: Path = PlatformDataFolderResolver.resolve(owner, dataFolderHint)
    init {
        DisplayZone.zone = ZoneId.of(config.timeZone)
    }

    private val startedAtMillis = System.currentTimeMillis()
    private val journalRuntime = RuntimeJournal(dataFolder, config, platformOwnership, startedAtMillis, EVENTS_COMMAND)
    private val journal get() = journalRuntime.store
    private val support = SupportRuntime(
        owner = owner,
        ownership = platformOwnership,
        identity = platformIdentity,
        platformDiagnostics = platformDiagnostics,
        platformLogObservation = platformLogObservation,
        platformLogging = platformLogging,
        diagnostics = diagnostics,
        config = config,
        dataFolder = dataFolder,
        runtimeDiagnostics = { runtimeDiagnostics.assemble() },
        journal = journalRuntime::record,
        onRuntimeError = { logOwner, error ->
            libraryMetrics?.errorReporter?.capture(
                throwable = error,
                operation = "pnlibrary.runtime",
                attributes = mapOf("source" to logOwner.javaClass.name),
            )
        },
    )
    override val observability get() = support.service
    override val activity: ActivityService get() = observability
    override val metrics: MetricsService get() = metricsService
    override val logging: LoggingService = ModuleLogging(
        platformLogging,
        platformOwnership,
        platformDiagnostics,
        platformIdentity,
        support.logs,
        capture = support.capture,
    )

    private val features = RuntimeFeatureSet(platform, dataFolder, support.capture).also { set ->
        set.configurations.fileListener = configFileDiagnostics(diagnostics)
    }
    private val configurationService get() = features.configurations
    override val configurations: ConfigurationService get() = configurationService
    private val updateService get() = features.updates
    private val directDownloadManager get() = features.downloads
    override val updates: UpdateService get() = updateService
    private val platformServices = PlatformServiceSet(
        commandAdapter = commandAdapter,
        audienceAdapter = audienceAdapter,
        taskAdapter = taskAdapter,
        platformLogging = support.capture,
        taskHistoryCapacity = config.taskHistoryCapacity,
        taskHistoryFile = dataFolder.resolve("task-history.tsv"),
        errorLogger = ::recordAndLog,
        commandFeedback = standardCommandFeedback(config),
    )
    private val taskService get() = platformServices.tasks
    override val runtimeTasks: TaskScope get() = taskService.scope(owner)

    private val mountable = LinkedHashMap<String, CommandDefinition>()

    override fun mountableCommand(name: String): CommandDefinition? = mountable[name]

    override fun journalJoinNotice(): List<Component> = journalRuntime.queries.joinNotice()

    override fun journalGroups(level: String?, sinceHours: Int, limit: Int): List<JournalGroup> =
        journalRuntime.queries.groups(level, sinceHours, limit)

    override fun taskSnapshots(): List<TaskSnapshot> = taskService.query().filterNot { it.hidden }

    override fun cancelTask(id: String): TaskControl =
        runCatchingNonFatal { taskService.cancelByAdmin(TaskId(id)) }.getOrDefault(TaskControl.UNAVAILABLE)

    override fun runTaskNow(id: String): TaskControl =
        runCatchingNonFatal { taskService.runNow(TaskId(id)) }.getOrDefault(TaskControl.UNAVAILABLE)

    override fun journalDetail(id: String): JournalDetail? = journalRuntime.queries.detail(id)

    private val commandService get() = platformServices.commands
    /** Runs a registered command for a viewer who is not on this server, or returns null when the command service cannot. */
    internal fun dispatchCommand(command: CommandDefinition, context: CommandContext): java.util.concurrent.CompletionStage<Void>? =
        (commandService as? PlatformCommandDispatcher)?.execute(command, context)

    override fun registerRuntimeCommand(command: CommandDefinition): CommandRegistration =
        commandService.register(owner, command)
    private val audienceService get() = platformServices.audiences
    override val audiences: AudienceService get() = audienceService
    private val serviceManager get() = features.services
    override val services: ServiceManager get() = serviceManager
    override val expansions: ExpansionManager get() = features.expansions
    private val eventService = EventDispatcher(
        EventHistory(dataFolder.resolve("event-history.tsv"), config.eventHistoryDays, { DisplayZone.zone }),
    ) { pluginId, message, error ->
        val identifiedMessage = "[$pluginId] $message"
        recordAndLog(owner, identifiedMessage, error)
    }
    override val events: EventService get() = eventService
    private val placeholderValueStore get() = features.placeholderValues
    private val placeholderHub get() = features.placeholders
    private val currencyFeature get() = features.currency
    private val platformProvider = NativePlatform()
    override val platforms: PlatformProvider get() = platformProvider
    override val placeholderAdapters: PlaceholderAdapterRegistry get() = placeholderHub
    override val placeholderValues: PlaceholderValueStore get() = placeholderValueStore
    override val plugins: PluginRegistry = PluginRegistryImpl(
        ownership = platformOwnership,
        identity = platformIdentity,
        remotePolicies = RemotePolicyMonitor(platformOwnership, platformExecution, RemotePolicyNoticeRenderer(support.capture)),
        events = eventService,
        tasks = taskService,
        services = serviceManager,
        logging = logging,
        libraryVersion = version,
        configurations = configurationService,
        metrics = metricsService,
        diagnostics = diagnostics,
        updates = updateService,
        shared = SharedFeatures(placeholderHub, currencyFeature, features.expansions, features.libraryExpansions),
        commands = commandService,
        directDownloads = directDownloadManager,
    )
    private val runtimeDiagnostics: RuntimeDiagnosticsAssembler = RuntimeDiagnosticsAssembler(
        version = version,
        isClosed = { isClosed },
        identity = platformIdentity,
        dataFolder = dataFolder,
        config = config,
        tasks = taskService,
        updates = updateService,
        diagnostics = diagnostics,
        support = support,
        metrics = metricsService,
        commands = commandService,
        journal = journalRuntime::summary,
    )

    fun init() {
        support.initialize { isClosed }
        startLibraryMetrics()
        // The platform's `/pn` control command holds these as `/pn debug` and `/pn events`.
        mountable["debug"] = diagnosticCommand(this, "/pn debug")
        mountable["events"] = journalRuntime.command()
        stats.start()
        mountable["stats"] = stats.command()
        mountable["placeholders"] = placeholdersCommand(placeholderHub)
        LibraryPlaceholders(placeholderHub, startedAtMillis, { plugins.all().size }, ::statisticValues).register()
        registerRuntimeCommand(
            libraryInfoCommand(this, platformIdentity) {
                platformOwnership.ownerDetails(owner)["version"] ?: "неизвестна"
            },
        )
    }

    override fun createDiagnosticReport(request: DebugRequest): DiagnosticReport {
        check(!isClosed) { "pnLibrary instance is closed" }
        return support.createDiagnosticReport(request)
    }

    private fun recordAndLog(logOwner: Any, message: String, error: Throwable) {
        support.recordRuntimeError(logOwner, message, error)
    }

    private val stats = StatsRuntime(::taskSnapshots, { events.inspector.events() }, journal)

    override fun statsSnapshot() = stats.snapshot()

    private fun statisticValues(): Map<String, Long> = stats.snapshot().let {
        mapOf(
            "errors" to it.errors24h.toLong(), "warnings" to it.warnings24h.toLong(), "tasks" to it.tasksActive.toLong(),
            "tasks.failed" to it.tasksFailed.toLong(), "events" to it.eventsCalled,
        )
    }

    private fun startLibraryMetrics() {
        libraryMetrics = RuntimeMetricsBootstrap(
            registry = metricsService,
            platform = platformIdentity,
            owner = owner,
            warning = { message -> logging.logger(owner, PnLibraryIdentity.NAME).warning(message) },
        ).start()
    }

    override fun close() {
        if (closedFlag.compareAndSet(false, true)) {
            RuntimeShutdownPlan(
                plugins = plugins,
                commands = commandService,
                audiences = audienceService,
                platforms = platformProvider,
                currency = currencyFeature,
                expansions = features.expansions,
                libraryExpansions = features.libraryExpansions,
                configurations = configurationService,
                libraryMetrics = { libraryMetrics },
                metrics = metricsService,
                updates = updateService,
                events = AutoCloseable(eventService::close),
                services = serviceManager,
                tasks = taskService,
                support = AutoCloseable { ResourceCleanup.closeAll({ stats.close() }, { support.close() }, { journalRuntime.close() }) },
                releaseProvider = { PnLibraryProvider.clear(this) },
                closePlatform = { closePlatform() },
                onClose = onClose,
            ).run()
            libraryMetrics = null
        }
    }

    override fun <T : Any> registerPlatform(type: Class<T>, implementation: T): AutoCloseable =
        platformProvider.register(type, implementation)

    private companion object {
        /** How the journal command is written in chat buttons and notices: it lives under `/pn` on every platform. */
        const val EVENTS_COMMAND = "/pn events"
    }
}
