package ru.privatenull.pnlibrary.core.updates

import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.plugin.PluginDependency
import ru.privatenull.pnlibrary.api.updates.*
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.update.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletionStage
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** One catalogue → resolver → verifier → transaction pipeline for every component. */
internal class UpdateServiceImpl(private val platform: PlatformAdapter, private val dataFolder: Path) : UpdateService, AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val entries = CopyOnWriteArrayList<RegisteredUpdate>()
    private val entriesLock = Any()
    private val configuration = UpdateConfiguration.load(dataFolder.resolve("updates.yml")) {
        platform.log(platform, LogLevel.WARNING, it)
    }
    private val executor = Executors.newSingleThreadScheduledExecutor { action ->
        Thread(action, "pnLibrary-update-orchestrator").apply { isDaemon = true }
    }
    private val catalogExecutor = Executors.newFixedThreadPool(4) { action ->
        Thread(action, "pnLibrary-catalog-fetch").apply { isDaemon = true }
    }
    private val catalogRequestExecutor = Executors.newFixedThreadPool(4) { action ->
        Thread(action, "pnLibrary-catalog-request").apply { isDaemon = true }
    }
    // Keep a manual `/pn update` responsive even when GitHub is unreachable.
    // The check can cover several registered products, so long per-request
    // timeouts otherwise add up and look like a frozen command.
    private val http = TrustedHttpClient(Duration.ofSeconds(2), Duration.ofSeconds(3))
    private val releaseCatalog = ReleaseCatalogClient(
        http, ReleaseCatalogueStore(dataFolder.resolve("updates/catalog")), catalogExecutor, Duration.ofMinutes(30),
    )
    private val catalogGateway = ReleaseCatalogGateway(
        client = releaseCatalog,
        http = http,
        requestExecutor = catalogRequestExecutor,
    )
    private val installer = UpdateInstaller(platform, dataFolder, configuration, http)
    private val freezes = FreezeStore(dataFolder.resolve("updates/freezes.json"))
    private val graphResolver = UpdateGraphResolver(platform, configuration, catalogGateway, freezes)
    private val orchestrator = UpdateOrchestrator(
        configuration, UpdateStateStore(dataFolder.resolve("updates"), warning = {
            platform.log(platform, LogLevel.WARNING, it)
        }), executor, ::resolveGraph, ::stageGraph, ::announce,
        automaticAllowed = ::automaticAllowed,
        remoteResolver = { resolveGraph(RefreshMode.FORCE_REMOTE) },
    ).also {
        runCatching { installer.recoverInterruptedTransactions() }.onFailure { error ->
            platform.log(platform, LogLevel.WARNING, "Update recovery failed", error)
        }
        it.start()
        platform.whenServerReady(Runnable {
            executor.execute {
                installer.reconcilePending(entries.toList(), it::healthResolved)
            }
        })
    }

    override fun register(
        owner: Any,
        product: ProductDescriptor,
        request: PluginUpdateRequest,
        dependencies: List<PluginDependency>,
    ): UpdateRegistration {
        check(!closed.get()) { "update service is closed" }
        val info = platform.ownerDetails(owner)
        val nativeProductName = info["name"] ?: request.repositoryName
        val version = info["version"] ?: error("Не удалось определить версию подключённого плагина")
        require(SemanticVersion.tryParse(version) != null) {
            "Version of $nativeProductName must be semantic: $version"
        }
        val jar = Paths.get(owner.javaClass.protectionDomain.codeSource.location.toURI()).toAbsolutePath().normalize()
        require(Files.isRegularFile(jar)) { "Плагин должен быть запущен из JAR" }
        val artifact = request.artifactFor(Runtime.version().feature(), platform.type)
            ?: error("Для Java ${Runtime.version().feature()} не зарегистрирован совместимый артефакт ${request.repositoryName}")
        require(product.version == SemanticVersion.parse(version)) {
            "Product version ${product.version} does not match native plugin version $version"
        }
        val registration = RegisteredUpdate(
            descriptor = product,
            request = request,
            artifact = artifact,
            jar = jar,
            updateDirectory = jar.parent.resolve("update"),
            selectedChannel = { graphResolver.channelFor(product.id, request.channel) },
            checkForUpdates = orchestrator::checkNow,
            stagePlan = { snapshot -> orchestrator.stage(snapshot.id) },
            onClose = ::removeRegistration,
        )
        synchronized(entriesLock) {
            check(!closed.get()) { "update service is closed" }
            entries += registration
        }
        orchestrator.registrationsChanged()
        return registration
    }

    override fun all(): List<UpdateRegistration> =
        java.util.Collections.unmodifiableList(ArrayList(entries))
    override fun get(product: String): UpdateRegistration? = entries.firstOrNull {
        it.product.equals(product, true) || it.request.repositoryName.equals(product, true)
    }
    @Deprecated("Use all()", ReplaceWith("all()"))
    override fun registrations(): List<UpdateRegistration> = all()
    @Deprecated("Use get(product)", ReplaceWith("get(product)"))
    override fun find(product: String): UpdateRegistration? = get(product)
    override fun checkNow(): CompletionStage<UpdatePlanSnapshot> = orchestrator.checkNow()
    override fun currentPlan(): Optional<UpdatePlanSnapshot> = orchestrator.currentPlan()
    override fun stage(planId: UUID): CompletionStage<UpdatePlanSnapshot> = orchestrator.stage(planId)
    override fun history(): List<UpdatePlanSnapshot> = orchestrator.history()
    override fun rollback(): CompletionStage<UpdatePlanSnapshot> = orchestrator.rollback {
        installer.rollbackLatest()
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(entriesLock) {
            entries.forEach(RegisteredUpdate::markClosed)
            entries.clear()
        }
        orchestrator.close()
        catalogExecutor.shutdownNow()
        catalogRequestExecutor.shutdownNow()
    }

    private fun resolveGraph(refreshMode: RefreshMode = RefreshMode.CACHED): ResolutionResult =
        graphResolver.resolve(entries.toList(), refreshMode)

    private fun automaticAllowed(snapshot: UpdatePlanSnapshot): Boolean {
        val changed = snapshot.plan?.changes?.map(ProductChange::product).orEmpty()
        return changed.all { component ->
            if (component.value == "pnlibrary") configuration.library.automaticDownload
            else {
                val policy = configuration.plugins[component.value]
                configuration.pluginUpdatesEnabled && policy?.enabled != false &&
                    (policy?.automaticDownload ?: (configuration.pluginAutomaticDownload ||
                        entries.firstOrNull { it.descriptor.id == component }?.request?.automaticDownload == true))
            }
        }
    }

    private fun stageGraph(snapshot: UpdatePlanSnapshot) {
        installer.stage(snapshot, entries.toList())
    }

    private fun announce(snapshot: UpdatePlanSnapshot) {
        UpdateAnnouncementRenderer.render(snapshot, entries.map { it.snapshot })
            .forEach { platform.console(platform, it) }
    }

    private fun removeRegistration(registration: RegisteredUpdate) {
        entries.remove(registration)
        orchestrator.registrationsChanged()
    }
}
