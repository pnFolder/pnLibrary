package org.pnfolder.pnlibrary.core.runtime

import org.pnfolder.pnlibrary.api.activity.ActivityService
import org.pnfolder.pnlibrary.api.audiences.AudienceService
import org.pnfolder.pnlibrary.api.commands.CommandDefinition
import org.pnfolder.pnlibrary.api.commands.CommandRegistration
import org.pnfolder.pnlibrary.api.config.ConfigurationService
import org.pnfolder.pnlibrary.api.currency.CurrencyProviderRegistry
import org.pnfolder.pnlibrary.api.diagnostics.DebugRequest
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticReport
import org.pnfolder.pnlibrary.api.events.EventService
import org.pnfolder.pnlibrary.api.logging.LoggingService
import org.pnfolder.pnlibrary.api.metrics.MetricsService
import org.pnfolder.pnlibrary.api.metrics.PluginMetrics
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderAdapterRegistry
import org.pnfolder.pnlibrary.api.placeholders.PlaceholderValueStore
import org.pnfolder.pnlibrary.api.platform.PlatformProvider
import org.pnfolder.pnlibrary.api.plugin.PluginRegistry
import org.pnfolder.pnlibrary.api.runtime.PnLibrary
import org.pnfolder.pnlibrary.api.runtime.PnLibraryConfig
import org.pnfolder.pnlibrary.api.runtime.PnLibraryProvider
import org.pnfolder.pnlibrary.api.services.ServiceManager
import org.pnfolder.pnlibrary.api.tasks.TaskScope
import org.pnfolder.pnlibrary.api.updates.UpdateService
import org.pnfolder.pnlibrary.api.version.SemanticVersion
import org.pnfolder.pnlibrary.config.ConfigurationServiceImpl
import org.pnfolder.pnlibrary.core.diagnostics.DiagnosticsRegistry
import org.pnfolder.pnlibrary.core.diagnostics.diagnosticCommand
import org.pnfolder.pnlibrary.core.journal.JournalLevel
import org.pnfolder.pnlibrary.core.journal.JournalRecord
import org.pnfolder.pnlibrary.core.journal.JournalStore
import org.pnfolder.pnlibrary.core.journal.journalCommand
import org.pnfolder.pnlibrary.core.journal.toJournalLevel
import net.kyori.adventure.text.Component
import org.pnfolder.pnlibrary.core.runtime.details.RuntimeDiagnosticsAssembler
import org.pnfolder.pnlibrary.core.runtime.details.RuntimeMetricsBootstrap
import org.pnfolder.pnlibrary.core.runtime.lifecycle.RuntimeResources
import org.pnfolder.pnlibrary.core.runtime.lifecycle.RuntimeShutdownPlan
import org.pnfolder.pnlibrary.core.runtime.lifecycle.journalJoinNotice
import org.pnfolder.pnlibrary.core.runtime.lifecycle.registerRuntimeCommand
import org.pnfolder.pnlibrary.core.runtime.platform.PlatformDataFolderResolver
import org.pnfolder.pnlibrary.core.runtime.platform.PlatformRegistration
import org.pnfolder.pnlibrary.core.runtime.platform.PlatformRuntimeContext
import org.pnfolder.pnlibrary.core.runtime.platform.PlatformServiceSet
import java.time.Duration
import org.pnfolder.pnlibrary.core.commands.standardCommandFeedback
import org.pnfolder.pnlibrary.core.journal.joinDigest
import java.time.ZoneId
import org.pnfolder.pnlibrary.core.downloads.DirectDownloadManager
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.core.downloads.DownloadConfiguration
import org.pnfolder.pnlibrary.core.events.EventServiceImpl
import org.pnfolder.pnlibrary.core.logging.LoggingServiceImpl
import org.pnfolder.pnlibrary.core.metrics.MetricsServiceImpl
import org.pnfolder.pnlibrary.core.observability.SupportRuntime
import org.pnfolder.pnlibrary.core.placeholders.GlobalPlaceholderValueStore
import org.pnfolder.pnlibrary.core.placeholders.PlaceholderHub
import org.pnfolder.pnlibrary.core.platform.PlatformProviderImpl
import org.pnfolder.pnlibrary.core.plugin.PluginRegistryImpl
import org.pnfolder.pnlibrary.core.services.ServiceManagerImpl
import org.pnfolder.pnlibrary.core.updates.service.UpdateServiceImpl
import org.pnfolder.pnlibrary.currency.CurrencyFeature
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
    init {
        DisplayZone.zone = ZoneId.of(config.timeZone)
    }

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
        journal = ::writeJournal,
    )
    override val observability get() = support.service
    override val activity: ActivityService get() = observability
    override val metrics: MetricsService get() = metricsService
    private val journal = JournalStore(
        dataFolder.resolve("journal"),
        mapOf(
            JournalLevel.INFO to Duration.ofDays(config.journalInfoDays.toLong()),
            JournalLevel.WARNING to Duration.ofDays(config.journalWarningDays.toLong()),
            JournalLevel.ERROR to Duration.ofDays(config.journalErrorDays.toLong()),
        ),
    ).also { store -> runCatching { store.prune() } }
    override val logging: LoggingService = LoggingServiceImpl(
        platformLogging,
        platformOwnership,
        platformDiagnostics,
        platformIdentity,
        support.logs,
        journal = ::writeJournal,
    )

    /** Adds one log line, from the library, a plugin or the platform itself, to the event journal. */
    private fun writeJournal(owner: Any, level: LogLevel, message: String, error: Throwable?) {
        journal.append(
            JournalRecord(
                timeMillis = System.currentTimeMillis(),
                level = level.toJournalLevel(),
                source = platformOwnership.ownerDetails(owner)["id"] ?: owner.javaClass.simpleName,
                type = if (error != null) "ERROR" else "LOG",
                message = message,
                details = error?.let {
                    mapOf(
                        "exception" to it.javaClass.name,
                        JournalRecord.STACKTRACE to JournalRecord.stackTraceOf(it),
                    )
                }.orEmpty(),
            ),
        )
    }
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
    ) { platformLogging.log(this, LogLevel.WARNING, it) }
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
        commandFeedback = standardCommandFeedback(config),
    )
    private val taskService get() = platformServices.tasks
    override val runtimeTasks: TaskScope get() = taskService.scope(owner)

    private val startedAtMillis = System.currentTimeMillis()

    private val mountable = LinkedHashMap<String, CommandDefinition>()

    override fun mountableCommand(name: String): CommandDefinition? = mountable[name]

    override fun journalJoinNotice(): List<Component> {
        if (!config.journalJoinNotice) return emptyList()
        val now = System.currentTimeMillis()
        val since = now - Duration.ofHours(config.journalJoinNoticeHours.toLong()).toMillis()
        return runCatching {
            joinDigest(journal.read(sinceMillis = since, limit = 5_000), now, config.journalJoinNoticeHours, startedAtMillis, EVENTS_COMMAND)
        }.getOrDefault(emptyList())
    }
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
        journal = { journal.summary() },
    )

    fun init() {
        support.initialize { isClosed }
        startLibraryMetrics()
        // The platform's `/pn` control command holds these as `/pn debug` and `/pn events`.
        mountable["debug"] = diagnosticCommand(this, "/pn debug")
        mountable["events"] = journalCommand(journal, EVENTS_COMMAND)
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

    private companion object {
        /** How the journal command is written in chat buttons and notices: it lives under `/pn` on every platform. */
        const val EVENTS_COMMAND = "/pn events"
    }
}
