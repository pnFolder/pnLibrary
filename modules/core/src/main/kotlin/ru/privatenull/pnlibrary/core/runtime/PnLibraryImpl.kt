package ru.privatenull.pnlibrary.core.runtime

import ru.privatenull.pnlibrary.api.diagnostics.DebugRequest
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticReport
import ru.privatenull.pnlibrary.api.logging.LoggingService
import ru.privatenull.pnlibrary.api.metrics.MetricsService
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.spi.platform.PlatformDiagnostics
import ru.privatenull.pnlibrary.spi.platform.PlatformExecution
import ru.privatenull.pnlibrary.spi.platform.PlatformIdentity
import ru.privatenull.pnlibrary.spi.platform.PlatformLogging
import ru.privatenull.pnlibrary.spi.platform.PlatformOwnership
import ru.privatenull.pnlibrary.api.runtime.PnLibrary
import ru.privatenull.pnlibrary.api.runtime.PnLibraryConfig
import ru.privatenull.pnlibrary.api.runtime.PnLibraryProvider
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.core.diagnostics.DiagnosticsRegistry
import ru.privatenull.pnlibrary.core.diagnostics.diagnosticCommand
import ru.privatenull.pnlibrary.core.config.ConfigurationServiceImpl
import ru.privatenull.pnlibrary.core.commands.CommandServiceImpl
import ru.privatenull.pnlibrary.core.audiences.AudienceServiceImpl
import ru.privatenull.pnlibrary.core.events.EventServiceImpl
import ru.privatenull.pnlibrary.core.logging.PlatformLoggingService
import ru.privatenull.pnlibrary.core.metrics.MetricsRegistry
import ru.privatenull.pnlibrary.core.plugin.PluginRegistryImpl
import ru.privatenull.pnlibrary.core.placeholders.PlaceholderHub
import ru.privatenull.pnlibrary.core.placeholders.GlobalPlaceholderValueStore
import ru.privatenull.pnlibrary.core.platform.PlatformProviderImpl
import ru.privatenull.pnlibrary.currency.CurrencyFeature
import ru.privatenull.pnlibrary.core.services.ServiceManagerImpl
import ru.privatenull.pnlibrary.core.tasks.TaskServiceImpl
import ru.privatenull.pnlibrary.api.tasks.TaskServiceSettings
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
import java.nio.file.Paths
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
    private val platform: PlatformAdapter,
    override val diagnostics: DiagnosticsRegistry,
    val config: PnLibraryConfig = PnLibraryConfig(),
    private val onClose: () -> Unit = {},
) : PnLibrary {

    override val configuration: PnLibraryConfig get() = config

    override val version: String = platform.ownerDetails(owner)["version"] ?: "unknown"
    override fun isAtLeastVersion(minimumVersion: String): Boolean =
        SemanticVersion.tryParse(version)?.isAtLeast(minimumVersion) ?: false

    private val closedFlag = AtomicBoolean(false)
    override val isClosed: Boolean get() = closedFlag.get()
    private val platformIdentity: PlatformIdentity = platform
    private val platformOwnership: PlatformOwnership = platform
    private val platformExecution: PlatformExecution = platform
    private val platformLogging: PlatformLogging = platform
    private val platformDiagnostics: PlatformDiagnostics = platform
    private val metricsRegistry = MetricsRegistry(platform.metricsFactory)
    private var libraryMetrics: PluginMetrics? = null
    val dataFolder: Path = platform.dataFolder ?: extractDataFolder(owner)
    private val support = SupportRuntime(
        owner = owner,
        ownership = platformOwnership,
        identity = platformIdentity,
        platformDiagnostics = platformDiagnostics,
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
        platformIdentity,
        platformExecution,
        dataFolder,
    )
    private val directDownloadConfiguration = DownloadConfiguration.load(
        dataFolder.resolve("downloads.yml"),
    )
    private val directDownloadManager = DirectDownloadManager(
        platformIdentity,
        platformOwnership,
        platformExecution,
        platformLogging,
        dataFolder,
        directDownloadConfiguration,
    )
    override val updates: UpdateService get() = updateService
    private val taskSettings = TaskServiceSettings(config.taskHistoryCapacity)
    private val taskService = TaskServiceImpl(
        adapter = platform.taskAdapter,
        settings = taskSettings,
        errorLogger = ::recordAndLog,
    )
    override val tasks: TaskService get() = taskService
    private val commandService = CommandServiceImpl(platform.commandAdapter, platformLogging)
    override val commands: CommandService get() = commandService
    private val audienceService = AudienceServiceImpl(platform.audienceAdapter)
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
        val platformCoverage = platform.diagnosticDetails(includeSensitive = false)["coverage"]
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
            "platform" to platform.id,
            "implementation" to platform.implementationName,
            "proxy" to platform.isProxy,
            "server" to platform.isServer,
            "dataFolder" to if (config.privacy) "[redacted]" else
                dataFolder.toAbsolutePath().normalize().toString(),
            "configuration" to linkedMapOf<String, Any?>(
                "privacy" to config.privacy,
                "configs" to config.configs,
                "logs" to config.logs,
                "upload" to config.upload,
                "uploadMode" to config.uploadMode,
                "historyRetentionDays" to config.historyRetentionDays,
                "historyMaxBytes" to config.historyMaxBytes,
                "logRecords" to config.logRecords,
                "taskHistoryCapacity" to config.taskHistoryCapacity,
                "keepReports" to config.keepReports,
                "maxReportBytes" to config.maxReportBytes,
                "excludedPathCount" to config.excludedPaths.size,
                "redactionRuleCount" to (
                    config.secretKeyPatterns.size + config.redactValuePatterns.size
                ),
            ),
        ),
        "tasks" to RuntimeTaskDetails.entries(taskSnapshots),
        "taskSummary" to RuntimeTaskDetails.summary(taskSnapshots),
        "updates" to updateSnapshots.map { registration ->
            val snapshot = registration.snapshot
            linkedMapOf<String, Any?>(
                "product" to snapshot.product,
                "repository" to registration.repository,
                "currentVersion" to snapshot.currentVersion,
                "latestVersion" to snapshot.latestVersion,
                "channel" to snapshot.channel.name,
                "state" to snapshot.state.name,
                "currentJava" to snapshot.currentJava,
                "requiredJava" to snapshot.requiredJava,
                "automaticDownload" to snapshot.automaticDownload,
                "releaseUrl" to snapshot.releaseUrl,
                "message" to snapshot.message,
                "availableReleases" to snapshot.availableReleases.map { release ->
                    linkedMapOf<String, Any?>(
                        "version" to release.version,
                        "channel" to release.channel.name,
                        "publishedAt" to release.publishedAt?.toString(),
                    )
                },
            )
        },
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
            val shutdown = RuntimeShutdown().apply {
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
                add { platform.close() }
                add { onClose() }
            }
            shutdown.run()
            libraryMetrics = null
        }
    }

    internal fun <T : Any> registerPlatform(type: Class<T>, implementation: T): AutoCloseable =
        platformProvider.register(type, implementation)

    private fun extractDataFolder(owner: Any): Path {
        return try {
            val method = owner.javaClass.getMethod("getDataFolder")
            val file = method.invoke(owner) as java.io.File
            file.toPath()
        } catch (_: Exception) {
            Paths.get("plugins", owner.javaClass.simpleName)
        }
    }

}
