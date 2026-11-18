package org.pnfolder.pnlibrary.core.runtime

import org.pnfolder.pnlibrary.core.plugin.LibraryWords
import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.api.audiences.AudienceService
import org.pnfolder.pnlibrary.api.commands.CommandContext
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.CommandRegistration
import org.pnfolder.pnlibrary.api.config.ConfigurationService
import org.pnfolder.pnlibrary.api.diagnostics.DebugRequest
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticReport
import org.pnfolder.pnlibrary.api.events.bus.EventService
import org.pnfolder.pnlibrary.api.expansions.ExpansionManager
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.logging.LoggingService
import org.pnfolder.pnlibrary.api.metrics.MetricsService
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapterRegistry
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderValueStore
import org.pnfolder.pnlibrary.api.platform.PlatformProvider
import org.pnfolder.pnlibrary.api.proxy.NetworkProxy
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
import org.pnfolder.pnlibrary.core.exceptions.ResourceCleanup
import org.pnfolder.pnlibrary.core.commands.settings.SettingsCommand
import org.pnfolder.pnlibrary.core.commands.standardCommandFeedback
import org.pnfolder.pnlibrary.core.diagnostics.DiagnosticsRegistry
import org.pnfolder.pnlibrary.core.diagnostics.diagnosticCommand
import org.pnfolder.pnlibrary.core.diagnostics.reportConfigFile
import org.pnfolder.pnlibrary.core.events.EventDispatcher
import org.pnfolder.pnlibrary.core.events.inspect.history.EventHistory
import org.pnfolder.pnlibrary.core.journal.JournalDetail
import org.pnfolder.pnlibrary.core.journal.JournalGroup
import org.pnfolder.pnlibrary.core.lifecycle.MountableCommands
import org.pnfolder.pnlibrary.core.lifecycle.RuntimeResources
import org.pnfolder.pnlibrary.core.lifecycle.cancelTask
import org.pnfolder.pnlibrary.core.lifecycle.dependencyDownloads
import org.pnfolder.pnlibrary.core.lifecycle.journalDetail
import org.pnfolder.pnlibrary.core.lifecycle.journalGroups
import org.pnfolder.pnlibrary.core.lifecycle.journalJoinNotice
import org.pnfolder.pnlibrary.core.lifecycle.mountableCommand
import org.pnfolder.pnlibrary.core.lifecycle.placeholderInfos
import org.pnfolder.pnlibrary.core.lifecycle.registerRuntimeCommand
import org.pnfolder.pnlibrary.core.lifecycle.runTaskNow
import org.pnfolder.pnlibrary.core.lifecycle.statsSnapshot
import org.pnfolder.pnlibrary.core.lifecycle.taskSnapshots
import org.pnfolder.pnlibrary.core.logging.ModuleLogging
import org.pnfolder.pnlibrary.core.metrics.MetricsSessions
import org.pnfolder.pnlibrary.core.observability.SupportRuntime
import org.pnfolder.pnlibrary.core.placeholders.builtin.LibraryPlaceholders
import org.pnfolder.pnlibrary.core.placeholders.placeholdersCommand
import org.pnfolder.pnlibrary.core.plugin.PluginRegistryImpl
import org.pnfolder.pnlibrary.core.plugin.SharedFeatures
import org.pnfolder.pnlibrary.core.remote.RemotePolicyMonitor
import org.pnfolder.pnlibrary.core.remote.RemotePolicyNoticeRenderer
import org.pnfolder.pnlibrary.core.runtime.details.RuntimeDiagnosticsAssembler
import org.pnfolder.pnlibrary.core.runtime.details.RuntimeFeatureSet
import org.pnfolder.pnlibrary.core.runtime.details.RuntimeJournal
import org.pnfolder.pnlibrary.core.runtime.details.RuntimeMetricsBootstrap
import org.pnfolder.pnlibrary.core.runtime.lifecycle.RuntimeShutdownPlan
import org.pnfolder.pnlibrary.core.runtime.platform.NativePlatform
import org.pnfolder.pnlibrary.core.runtime.platform.PlatformRegistration
import org.pnfolder.pnlibrary.core.runtime.platform.PlatformRuntimeContext
import org.pnfolder.pnlibrary.core.runtime.platform.PlatformServiceSet
import org.pnfolder.pnlibrary.core.settings.LegacySettingsImport
import org.pnfolder.pnlibrary.core.settings.LibraryInfoFiles
import org.pnfolder.pnlibrary.core.settings.SettingPickerHook
import org.pnfolder.pnlibrary.core.settings.SettingsStore
import org.pnfolder.pnlibrary.core.stats.StatsRuntime
import org.pnfolder.pnlibrary.core.tasks.TaskControl
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandDispatcher
import org.pnfolder.pnlibrary.updates.paths.LegacyPathsMigration
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths
import java.nio.file.Path
import java.nio.file.Paths
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
    presetConfig: PnLibraryConfig? = null,
    private val onClose: () -> Unit = {},
) : PnLibrary, PlatformRegistration, RuntimeResources {
    private val closedFlag = AtomicBoolean(false)
    private val startedAtMillis = System.currentTimeMillis()

    override val configuration: PnLibraryConfig get() = config
    override val isClosed: Boolean get() = closedFlag.get()
    override val version: String = platform.ownership.ownerDetails(owner)["version"] ?: "unknown"
    val dataFolder: Path = platform.dataFolder
        ?: platform.ownership.ownerDataFolder(owner)
        ?: Paths.get("plugins", owner.javaClass.simpleName)
    private val paths = LibraryPaths(dataFolder)

    /** Every setting an administrator may change; the command `/pn settings` and the menu edit it. */
    private val settings: SettingsStore
    private val config: PnLibraryConfig

    init {
        val warn = { text: String -> platform.logging.log(owner, LogLevel.WARNING, text) }
        // The old files are moved into data and read once, before the settings are used.
        LegacyPathsMigration(paths, warn).run()
        settings = SettingsStore(paths.settings, warn).also { LegacySettingsImport(paths, warn).run(it) }
        config = presetConfig ?: settings.config()
        DisplayZone.zone = ZoneId.of(settings.current().timeZone)
        settings.onChange { DisplayZone.zone = ZoneId.of(it.timeZone) }
        // Help and settings overview for administrators: written again at every start and after every change.
        LibraryInfoFiles(paths, settings::values, version, warning = warn).also { files ->
            files.refresh()
            settings.onChange { files.refreshSettings() }
        }
    }

    // Support and journal come first: every later part reports its failures through them.
    private val journalRuntime = RuntimeJournal(
        paths.journal, config, platform.ownership, startedAtMillis, EVENTS_COMMAND,
        onWriteFailure = ::reportJournalFailure,
    )

    // Written by init and close, read by error reports from any thread.
    @Volatile private var libraryMetrics: PluginMetrics? = null
    private val support: SupportRuntime = SupportRuntime(
        owner = owner,
        ownership = platform.ownership,
        identity = platform.identity,
        platformDiagnostics = platform.diagnostics,
        platformLogObservation = platform.logObservation,
        platformLogging = platform.logging,
        diagnostics = diagnostics,
        config = config,
        paths = paths,
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

    private val features = RuntimeFeatureSet(platform, paths, support.capture, settings).also { set ->
        set.configurations.fileListener = { file ->
            val handles = listOfNotNull(diagnostics.reportConfigFile(file), set.updates.databaseSources.register(file))
            AutoCloseable { handles.forEach(AutoCloseable::close) }
        }
        LibraryWords.register(set.configurations.scope(owner))
    }
    private val platformServices = PlatformServiceSet(
        commandAdapter = platform.commandAdapter,
        audienceAdapter = platform.audienceAdapter,
        taskAdapter = platform.taskAdapter,
        platformLogging = support.capture,
        taskHistoryCapacity = config.taskHistoryCapacity,
        taskHistoryFile = paths.taskHistory,
        errorLogger = support::recordRuntimeError,
        commandFeedback = standardCommandFeedback(config),
        onAudienceFailure = { error ->
            support.recordRuntimeError(owner, "[pnLibrary/audiences] A message could not be delivered to a player", error)
        },
    )
    private val tasks = platformServices.tasks
    private val commands = platformServices.commands
    private val eventService = EventDispatcher(
        EventHistory(
            paths.eventHistory, config.eventHistoryDays, { DisplayZone.zone },
            onFailure = { support.recordRuntimeError(owner, "[pnLibrary/events] The event history could not be read or saved", it) },
        ),
    ) { pluginId, message, error -> support.recordRuntimeError(owner, "[$pluginId] $message", error) }
    private val platformProvider = NativePlatform()

    override val observability get() = support.service
    override val metrics: MetricsService get() = metricsService
    override val configurations: ConfigurationService get() = features.configurations
    override val updates: UpdateService get() = features.updates
    override val runtimeTasks: TaskScope get() = tasks.scope(owner)
    override val audiences: AudienceService get() = platformServices.audiences
    override val services: ServiceManager get() = features.services
    override val expansions: ExpansionManager get() = features.expansions
    override val events: EventService get() = eventService
    override val platforms: PlatformProvider get() = platformProvider
    override val proxy: NetworkProxy? = platform.networkProxy
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
    private val stats = StatsRuntime(::taskSnapshots, { events.inspector.events() }, journalRuntime.store) { error ->
        support.recordRuntimeError(owner, "[pnLibrary/stats] Could not record statistics", error)
    }
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
        mountable[MountableCommands.DEBUG] = diagnosticCommand(this, "/pn debug")
        mountable[MountableCommands.EVENTS] = journalRuntime.command()
        mountable[MountableCommands.STATS] = stats.command()
        mountable[MountableCommands.PLACEHOLDERS] = placeholdersCommand(features.placeholders)
        mountable[MountableCommands.SETTINGS] = SettingsCommand(settings, settingPicker).definition()
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

    override fun dependencyDownloads() = features.downloads

    override fun librarySettings(): SettingsStore = settings

    override val settingPicker = SettingPickerHook()

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

    /** The journal cannot record its own failure, so the console and the error tracker get it directly. */
    private fun reportJournalFailure(error: Throwable) {
        platform.logging.console(owner, "[pnLibrary/journal] Could not write to the journal: ${error.javaClass.simpleName}: ${error.message}")
        reportRuntimeError(owner, error)
    }

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
