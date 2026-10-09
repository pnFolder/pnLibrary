package ru.privatenull.pnlibrary.core.runtime

import ru.privatenull.pnlibrary.api.diagnostics.DebugRequest
import ru.privatenull.pnlibrary.api.diagnostics.DiagnosticReport
import ru.privatenull.pnlibrary.api.logging.LoggingService
import ru.privatenull.pnlibrary.api.metrics.MetricsService
import ru.privatenull.pnlibrary.api.metrics.MetricsProvider
import ru.privatenull.pnlibrary.api.metrics.MetricsProviderConfiguration
import ru.privatenull.pnlibrary.api.metrics.PluginMetrics
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
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
    private val metricsRegistry = MetricsRegistry(platform.metricsFactory)
    private var libraryMetrics: PluginMetrics? = null
    val dataFolder: Path = platform.dataFolder ?: extractDataFolder(owner)
    private val support = SupportRuntime(
        owner = owner,
        platform = platform,
        diagnostics = diagnostics,
        config = config,
        dataFolder = dataFolder,
        runtimeDiagnostics = ::runtimeDiagnostics,
    )
    override val observability get() = support.service
    override val activity: ActivityService get() = observability
    override val metrics: MetricsService get() = metricsRegistry
    override val logging: LoggingService = PlatformLoggingService(platform, support.logs)
    private val configurationService = ConfigurationServiceImpl(platform)
    override val configurations: ConfigurationService get() = configurationService
    private val updateService = UpdateServiceImpl(platform, dataFolder)
    private val directDownloadConfiguration = DownloadConfiguration.load(
        dataFolder.resolve("downloads.yml"),
    )
    private val directDownloadManager = DirectDownloadManager(
        platform,
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
    private val commandService = CommandServiceImpl(platform)
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
    private val placeholderHub = PlaceholderHub(platform, placeholderValueStore)
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
        platform = platform,
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

    private fun runtimeDiagnostics(): Map<String, Any?> = linkedMapOf(
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
        "tasks" to tasks.query().map { task ->
            linkedMapOf<String, Any?>(
                "id" to task.id.value,
                "name" to task.name,
                "owner" to task.ownerName,
                "execution" to task.executionKind.name,
                "status" to task.status.name,
                "runCount" to task.runCount,
                "skippedCount" to task.skippedCount,
                "lastFailure" to task.lastFailure,
            )
        },
        "updates" to updates.all().map { registration ->
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
        "metrics" to metricsRegistry.diagnosticSnapshot(),
        "diagnostics" to diagnostics.diagnosticSummary(),
        "updateGraph" to linkedMapOf<String, Any?>(
            "current" to updates.currentPlan().orElse(null)?.let(::updatePlanDetails),
            "history" to updates.history().map(::updatePlanDetails),
        ),
    )

    private fun updatePlanDetails(snapshot: ru.privatenull.pnlibrary.api.updates.UpdatePlanSnapshot): Map<String, Any?> =
        linkedMapOf(
            "id" to snapshot.id.toString(),
            "revision" to snapshot.revision,
            "state" to snapshot.state.name,
            "message" to snapshot.message,
            "blockers" to snapshot.blockers.map { it.javaClass.simpleName },
            "targetApi" to snapshot.plan?.targetApi,
            "changeCount" to snapshot.plan?.changes?.size,
            "selectedReleaseCount" to snapshot.plan?.selected?.size,
        )

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
        var openedSession: PluginMetrics? = null
        val session = runCatching {
            metricsRegistry.open(
                owner,
                listOf(
                    MetricsProviderConfiguration(
                        provider = MetricsProvider.FASTSTATS,
                        token = FASTSTATS_TOKEN,
                    ),
                ),
            ).also { metrics ->
                openedSession = metrics
                metrics.simplePie("platform") { platform.type.name.lowercase() }
                metrics.simplePie("implementation") { platform.implementationName }
                metrics.start()
            }
        }.onFailure { error ->
            runCatching { openedSession?.close() }
            logging.logger(owner, "pnLibrary").warning(
                "FastStats metrics could not be started: ${error.message ?: error.javaClass.simpleName}",
            )
        }.getOrNull()
        libraryMetrics = session
    }

    override fun close() {
        if (closedFlag.compareAndSet(false, true)) {
            runCatching { plugins.close() }
            runCatching { commandService.close() }
            runCatching { audienceService.close() }
            runCatching { platformProvider.close() }
            runCatching { currencyFeature.close() }
            runCatching { configurationService.close() }
            runCatching { libraryMetrics?.close() }
            libraryMetrics = null
            runCatching { metricsRegistry.close() }
            runCatching { updateService.close() }
            runCatching { eventService.close() }
            runCatching { serviceManager.close() }
            runCatching { taskService.close() }
            runCatching { support.close() }
            PnLibraryProvider.clear(this)
            runCatching { platform.close() }
            onClose()
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

    private companion object {
        const val FASTSTATS_TOKEN = "009192430b9bfffa176e8cc8dd67cad7"
    }
}
