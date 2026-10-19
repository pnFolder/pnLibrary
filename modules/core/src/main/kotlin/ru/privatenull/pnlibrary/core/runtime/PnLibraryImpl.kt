package ru.privatenull.pnlibrary.core.runtime

import ru.privatenull.pnlibrary.api.activity.ActivityService
import ru.privatenull.pnlibrary.api.audiences.AudienceService
import ru.privatenull.pnlibrary.api.commands.CommandDefinition
import ru.privatenull.pnlibrary.api.commands.CommandRegistration
import ru.privatenull.pnlibrary.api.config.ConfigurationService
import ru.privatenull.pnlibrary.api.currency.CurrencyProviderRegistry
import ru.privatenull.pnlibrary.api.diagnostics.DebugRequest
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticReport
import ru.privatenull.pnlibrary.api.events.EventService
import ru.privatenull.pnlibrary.api.logging.LoggingService
import ru.privatenull.pnlibrary.api.metrics.MetricsService
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderAdapterRegistry
import ru.privatenull.pnlibrary.api.placeholders.PlaceholderValueStore
import ru.privatenull.pnlibrary.api.platform.PlatformProvider
import ru.privatenull.pnlibrary.api.plugin.PluginRegistry
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.api.runtime.PnLibraryConfig
import ru.privatenull.pnlibrary.api.runtime.PnLibraryProvider
import ru.privatenull.pnlibrary.api.services.ServiceManager
import ru.privatenull.pnlibrary.api.tasks.TaskScope
import ru.privatenull.pnlibrary.api.updates.UpdateService
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.config.ConfigurationServiceImpl
import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticsRegistry
import ru.privatenull.pnlibrary.core.diagnostics.diagnosticCommand
import ru.privatenull.pnlibrary.core.downloads.DirectDownloadManager
import ru.privatenull.pnlibrary.core.downloads.DownloadConfiguration
import ru.privatenull.pnlibrary.core.events.EventServiceImpl
import ru.privatenull.pnlibrary.core.logging.LoggingServiceImpl
import ru.privatenull.pnlibrary.core.metrics.MetricsServiceImpl
import ru.privatenull.pnlibrary.core.observability.SupportRuntime
import ru.privatenull.pnlibrary.core.placeholders.GlobalPlaceholderValueStore
import ru.privatenull.pnlibrary.core.placeholders.PlaceholderHub
import ru.privatenull.pnlibrary.core.platform.PlatformProviderImpl
import ru.privatenull.pnlibrary.core.plugin.PluginRegistryImpl
import ru.privatenull.pnlibrary.core.services.ServiceManagerImpl
import ru.privatenull.pnlibrary.core.updates.service.UpdateServiceImpl
import ru.privatenull.pnlibrary.currency.CurrencyFeature
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
) : PnLibrary, PlatformRegistration, RuntimeResources {

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
    private val metricsService = MetricsServiceImpl(metricsFactory)
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
        runtimeDiagnostics = { runtimeDiagnostics.assemble() },
    )
    override val observability get() = support.service
    override val activity: ActivityService get() = observability
    override val metrics: MetricsService get() = metricsService
    override val logging: LoggingService = LoggingServiceImpl(
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
    override val runtimeTasks: TaskScope get() = taskService.scope(owner)
    private val commandService get() = platformServices.commands
    override fun registerRuntimeCommand(command: CommandDefinition): CommandRegistration =
        commandService.register(owner, command)
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
        metrics = metricsService,
        diagnostics = diagnostics,
        updates = updateService,
        placeholderHub = placeholderHub,
        currencyFeature = currencyFeature,
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
    )

    fun init() {
        support.initialize { isClosed }
        startLibraryMetrics()
        registerRuntimeCommand(diagnosticCommand(this))
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
            registry = metricsService,
            platform = platformIdentity,
            owner = owner,
            warning = { message -> logging.logger(owner, "pnLibrary").warning(message) },
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
                configurations = configurationService,
                libraryMetrics = { libraryMetrics },
                metrics = metricsService,
                updates = updateService,
                events = AutoCloseable(eventService::close),
                services = serviceManager,
                tasks = taskService,
                support = AutoCloseable(support::close),
                releaseProvider = { PnLibraryProvider.clear(this) },
                closePlatform = { closePlatform() },
                onClose = onClose,
            ).run()
            libraryMetrics = null
        }
    }

    override fun <T : Any> registerPlatform(type: Class<T>, implementation: T): AutoCloseable =
        platformProvider.register(type, implementation)
}
