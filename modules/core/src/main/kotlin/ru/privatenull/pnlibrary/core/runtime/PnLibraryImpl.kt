package ru.privatenull.pnlibrary.core.runtime

import ru.privatenull.pnlibrary.api.diagnostics.DebugRequest
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticReport
import ru.privatenull.pnlibrary.api.logging.LoggingService
import ru.privatenull.pnlibrary.api.metrics.MetricsService
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.spi.platform.PlatformDiagnostics
import ru.privatenull.pnlibrary.spi.platform.PlatformExecution
import ru.privatenull.pnlibrary.spi.platform.PlatformReadiness
import ru.privatenull.pnlibrary.spi.platform.PlatformIdentity
import ru.privatenull.pnlibrary.spi.platform.PlatformLogging
import ru.privatenull.pnlibrary.spi.platform.PlatformLogObservation
import ru.privatenull.pnlibrary.spi.platform.PlatformOwnership
import ru.privatenull.pnlibrary.spi.metrics.PlatformMetricsFactory
import ru.privatenull.pnlibrary.spi.commands.PlatformCommandAdapter
import ru.privatenull.pnlibrary.spi.audiences.PlatformAudienceAdapter
import ru.privatenull.pnlibrary.spi.tasks.PlatformTaskAdapter
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.api.runtime.PnLibraryConfig
import ru.privatenull.pnlibrary.api.runtime.PnLibraryProvider
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticsRegistry
import ru.privatenull.pnlibrary.core.diagnostics.diagnosticCommand
import ru.privatenull.pnlibrary.core.config.ConfigurationServiceImpl
import ru.privatenull.pnlibrary.core.events.EventServiceImpl
import ru.privatenull.pnlibrary.core.logging.PlatformLoggingService
import ru.privatenull.pnlibrary.core.metrics.MetricsRegistry
import ru.privatenull.pnlibrary.core.plugin.PluginRegistryImpl
import ru.privatenull.pnlibrary.core.placeholders.PlaceholderHub
import ru.privatenull.pnlibrary.core.placeholders.GlobalPlaceholderValueStore
import ru.privatenull.pnlibrary.core.platform.PlatformProviderImpl
import ru.privatenull.pnlibrary.currency.CurrencyFeature
import ru.privatenull.pnlibrary.core.services.ServiceManagerImpl
import ru.privatenull.pnlibrary.api.activity.ActivityService
import ru.privatenull.pnlibrary.api.audiences.AudienceService
import ru.privatenull.pnlibrary.api.commands.CommandService
import ru.privatenull.pnlibrary.api.config.ConfigurationService
import ru.privatenull.pnlibrary.api.currency.CurrencyProviderRegistry
import ru.privatenull.pnlibrary.api.events.EventService
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderAdapterRegistry
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderValueStore
import ru.privatenull.pnlibrary.api.platform.PlatformProvider
import ru.privatenull.pnlibrary.api.plugin.PluginRegistry
import ru.privatenull.pnlibrary.api.services.ServiceManager
import ru.privatenull.pnlibrary.api.tasks.TaskService
import ru.privatenull.pnlibrary.api.updates.UpdateService
import ru.privatenull.pnlibrary.core.downloads.DirectDownloadManager
import ru.privatenull.pnlibrary.core.downloads.DownloadConfiguration
import ru.privatenull.pnlibrary.core.observability.SupportRuntime
import ru.privatenull.pnlibrary.core.updates.UpdateServiceImpl
import java.nio.file.Path
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
) : PnLibrary, PlatformRegistration {

    // Keep the service wiring readable while the platform boundary remains one context object.
    private val platformIdentity get() = platform.identity
    private val platformOwnership get() = platform.ownership
    private val platformExecution get() = platform.execution
    private val platformReadiness get() = platform.readiness
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
    private val metricsRegistry = MetricsRegistry(metricsFactory)
    private var libraryMetrics: PluginMetrics? = null
    val dataFolder: Path = PlatformDataFolderResolver.resolve(owner, dataFolderHint)
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
        runtimeDiagnostics = ::runtimeDiagnostics,
    )
    override val observability get() = support.service
    override val activity: ActivityService get() = observability
    override val metrics: MetricsService get() = metricsRegistry
    override val logging: LoggingService = PlatformLoggingService(
        platformLogging,
        platformOwnership,
        platformDiagnostics,
        platformIdentity,
        support.logs,
    )
    private val configurationService = ConfigurationServiceImpl(platformOwnership)
    override val configurations: ConfigurationService get() = configurationService
    private val updateService = UpdateServiceImpl(
        platformLogging,
        platformOwnership,
        platformOwnership,
        platformIdentity,
        platformExecution,
        platformReadiness,
        dataFolder,
    )
    private val directDownloadConfiguration = DownloadConfiguration.load(
        dataFolder.resolve("downloads.yml"),
    )
    private val directDownloadManager = DirectDownloadManager(
        platformIdentity,
        platformOwnership,
        platformOwnership,
        platformExecution,
        platformReadiness,
        platformLogging,
        dataFolder,
        directDownloadConfiguration,
    )
    override val updates: UpdateService get() = updateService
    private val platformServices = PlatformServiceSet(
        commandAdapter = commandAdapter,
        audienceAdapter = audienceAdapter,
        taskAdapter = taskAdapter,
        platformLogging = platformLogging,
        taskHistoryCapacity = config.taskHistoryCapacity,
        errorLogger = ::recordAndLog,
    )
    private val taskService get() = platformServices.tasks
    override val tasks: TaskService get() = taskService
    private val commandService get() = platformServices.commands
    override val commands: CommandService get() = commandService
    private val audienceService get() = platformServices.audiences
    override val audiences: AudienceService get() = audienceService
    private val serviceManager = ServiceManagerImpl()
    override val services: ServiceManager get() = serviceManager
    private val eventService = EventServiceImpl { pluginId, message, error ->
        val identifiedMessage = "[$pluginId] $message"
        recordAndLog(owner, identifiedMessage, error)
    }
    override val events: EventService get() = eventService
    private val placeholderValueStore = GlobalPlaceholderValueStore()
    private val placeholderHub = PlaceholderHub(platformIdentity, placeholderValueStore)
    private val currencyFeature = CurrencyFeature()
    private val platformProvider = PlatformProviderImpl()
    override val platforms: PlatformProvider get() = platformProvider
    override val placeholderAdapters: PlaceholderAdapterRegistry get() = placeholderHub
    override val placeholderValues: PlaceholderValueStore get() = placeholderValueStore
    init {
        serviceManager.register(
            CurrencyProviderRegistry::class.java,
            currencyFeature.providers,
        )
    }
    override val plugins: PluginRegistry = PluginRegistryImpl(
        ownership = platformOwnership,
        identity = platformIdentity,
        execution = platformExecution,
        platformLogging = platformLogging,
        events = eventService,
        tasks = taskService,
        services = serviceManager,
        logging = logging,
        libraryVersion = version,
        configurations = configurationService,
        metrics = metricsRegistry,
        diagnostics = diagnostics,
        updates = updateService,
        placeholderHub = placeholderHub,
        currencyFeature = currencyFeature,
        commands = commandService,
        directDownloads = directDownloadManager,
    )

    private fun runtimeDiagnostics(): Map<String, Any?> {
        val taskSnapshots = tasks.query()
        val updateSnapshots = updates.all()
        val platformCoverage = platformDiagnostics.diagnosticDetails(includeSensitive = false)["coverage"]
        val taskFailures = taskSnapshots.count { it.lastFailure != null }
        val failedUpdates = updateSnapshots.count { it.snapshot.state.name.equals("FAILED", ignoreCase = true) }
        val diagnosticSummary = diagnostics.diagnosticSummary()
        val history = diagnostics.historyAnalytics()
        val nativeLogs = support.logSummary()
        val reports = support.reportSummary()
        val observability = support.observabilityAnalytics()
        return linkedMapOf(
        "library" to linkedMapOf<String, Any?>(
            "version" to version,
            "closed" to isClosed,
            "platform" to platformIdentity.id,
            "implementation" to platformIdentity.implementationName,
            "proxy" to platformIdentity.isProxy,
            "server" to platformIdentity.isServer,
            "dataFolder" to if (config.privacy) "[redacted]" else
                dataFolder.toAbsolutePath().normalize().toString(),
            "configuration" to RuntimeConfigurationDetails.of(config),
        ),
        "tasks" to RuntimeTaskDetails.entries(taskSnapshots),
        "taskSummary" to RuntimeTaskDetails.summary(taskSnapshots),
        "updates" to updateSnapshots.map(RuntimeUpdateDetails::registration),
        "updateSummary" to RuntimeUpdateSummary.of(updateSnapshots),
        "analytics" to RuntimeAnalytics.of(
            taskCount = taskSnapshots.size,
            updateCount = updateSnapshots.size,
            taskFailures = taskFailures,
            failedUpdates = failedUpdates,
            diagnosticSummary = diagnosticSummary,
            history = history,
            nativeLogs = nativeLogs,
            reports = reports,
            observability = observability,
        ),
        "metrics" to metricsRegistry.diagnosticSnapshot(),
        "metricsAnalytics" to metricsRegistry.diagnosticAnalytics(),
        "commandsAnalytics" to commandService.diagnosticSnapshot(),
        "platformCoverage" to platformCoverage,
        "diagnostics" to diagnostics.diagnosticSummary(),
        "diagnosticHistory" to diagnostics.historyAnalytics(),
        "nativeLogs" to nativeLogs,
        "reports" to reports,
        "observability" to observability,
        "configurationDeclarations" to diagnostics.configurations("all").map { declaration ->
            linkedMapOf<String, Any?>(
                "plugin" to declaration.plugin,
                "path" to declaration.configuration.path,
                "dataDirectory" to if (config.privacy) "[redacted]"
                else declaration.dataDirectory?.toString(),
            )
        },
        "updateGraph" to linkedMapOf<String, Any?>(
            "current" to updates.currentPlan().orElse(null)?.let(RuntimeUpdateDetails::of),
            "history" to updates.history().map(RuntimeUpdateDetails::of),
        ),
        )
    }

    fun init() {
        support.initialize { isClosed }
        startLibraryMetrics()
        commands.register(owner, diagnosticCommand(this))
    }

    override fun createDiagnosticReport(request: DebugRequest): DiagnosticReport {
        check(!isClosed) { "pnLibrary instance is closed" }
        return support.createDiagnosticReport(request)
    }

    private fun recordAndLog(logOwner: Any, message: String, error: Throwable) {
        support.recordRuntimeError(logOwner, message, error)
        runCatching {
            libraryMetrics?.errorReporter?.capture(
                throwable = error,
                operation = "pnlibrary.runtime",
                attributes = mapOf("source" to logOwner.javaClass.name),
            )
        }
    }

    private fun startLibraryMetrics() {
        libraryMetrics = RuntimeMetricsBootstrap(
            registry = metricsRegistry,
            platform = platformIdentity,
            owner = owner,
            warning = { message -> logging.logger(owner, "pnLibrary").warning(message) },
        ).start()
    }

    override fun close() {
        if (closedFlag.compareAndSet(false, true)) {
            createShutdownPlan().run()
            libraryMetrics = null
        }
    }

    /**
     * Keeps the runtime shutdown order in one named lifecycle boundary.
     * The composition root only decides when to close; this method owns what
     * must be closed and in which order.
     */
    private fun createShutdownPlan(): RuntimeShutdown = RuntimeShutdown().apply {
        add { plugins.close() }
        add { commandService.close() }
        add { audienceService.close() }
        add { platformProvider.close() }
        add { currencyFeature.close() }
        add { configurationService.close() }
        add { libraryMetrics?.close() }
        add { metricsRegistry.close() }
        add { updateService.close() }
        add { eventService.close() }
        add { serviceManager.close() }
        add { taskService.close() }
        add { support.close() }
        add { PnLibraryProvider.clear(this@PnLibraryImpl) }
        add { closePlatform() }
        add { onClose() }
    }

    override fun <T : Any> registerPlatform(type: Class<T>, implementation: T): AutoCloseable =
        platformProvider.register(type, implementation)

}
