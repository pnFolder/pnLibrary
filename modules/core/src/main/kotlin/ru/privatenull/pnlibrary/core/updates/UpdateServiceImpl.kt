package ru.privatenull.pnlibrary.core.updates

import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.plugin.PluginDependency
import ru.privatenull.pnlibrary.api.updates.*
import ru.privatenull.pnlibrary.api.version.PnLibraryApi
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
    private val transaction = UpdateTransaction(
        dataFolder.resolve("updates/transactions"), ArtifactVerifier(MAX_ARTIFACT_BYTES), dataFolder.parent,
    )
    private val planStager = UpdatePlanStager(
        platform = platform.type,
        dataFolder = dataFolder,
        http = http,
        transaction = transaction,
        maximumArtifactBytes = MAX_ARTIFACT_BYTES,
    )
    private val freezes = FreezeStore(dataFolder.resolve("updates/freezes.json"))
    private val orchestrator = UpdateOrchestrator(
        configuration, UpdateStateStore(dataFolder.resolve("updates"), warning = {
            platform.log(platform, LogLevel.WARNING, it)
        }), executor, ::resolveGraph, ::stageGraph, ::announce,
        automaticAllowed = ::automaticAllowed,
        remoteResolver = { resolveGraph(RefreshMode.FORCE_REMOTE) },
    ).also {
        runCatching { transaction.recoverAll() }.onFailure { error ->
            platform.log(platform, LogLevel.WARNING, "Update recovery failed", error)
        }
        it.start()
        platform.whenServerReady(Runnable { executor.execute { reconcilePendingUpdates(it) } })
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
            selectedChannel = { configuredChannel(product.id, request.channel) },
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
        val candidate = transaction.latestRollbackCandidate()
            ?: error("Нет сохранённого набора JAR для отката")
        transaction.rollback(candidate)
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

    private fun resolveGraph(refreshMode: RefreshMode = RefreshMode.CACHED): ResolutionResult {
        val registrations = entries.toList()
        if (registrations.isEmpty()) return emptyUpdatePlan()

        val installed = installedProducts(registrations)
        val channels = updateChannels(registrations)
        val releases = requestReleaseCatalogs(registrations, refreshMode)
        observeLatestVersions(registrations, releases)

        return UpdateResolver(ProductId.of("pnlibrary")).resolve(
            installed = installed,
            releases = releases,
            channels = channels,
            defaultChannel = UpdateChannel.STABLE,
            frozen = frozenProducts(registrations),
            platform = platform.type,
            javaFeature = Runtime.version().feature(),
            policy = resolverPolicy(),
        )
    }

    private fun emptyUpdatePlan(): ResolutionResult =
        ResolutionResult.Ready(UpdatePlan(PnLibraryApi.VERSION, emptyList(), emptyList()))

    private fun installedProducts(registrations: List<RegisteredUpdate>): List<InstalledProduct> =
        registrations.map { entry ->
            entry.installedProduct(PnLibraryApi.VERSION.takeIf { entry.descriptor.id.value == "pnlibrary" })
        }

    private fun updateChannels(registrations: List<RegisteredUpdate>): Map<ProductId, UpdateChannel> =
        registrations.associate { entry -> entry.descriptor.id to configuredChannel(entry) }

    private fun configuredChannel(entry: RegisteredUpdate): UpdateChannel =
        configuredChannel(entry.descriptor.id, entry.request.channel)

    private fun configuredChannel(product: ProductId, fallback: UpdateChannel): UpdateChannel =
        if (product.value == "pnlibrary") configuration.library.channel
        else configuration.plugins[product.value]?.channel ?: fallback

    private fun requestReleaseCatalogs(
        registrations: List<RegisteredUpdate>,
        refreshMode: RefreshMode,
    ): List<ProductRelease> = registrations.flatMap { registration ->
        val catalog = catalogGateway.load(
            owner = registration.request.repositoryOwner,
            repository = registration.request.repositoryName,
            refreshMode = refreshMode,
        )
        require(catalog.product.equals(registration.descriptor.id.value, ignoreCase = true)) {
            "Release catalog product ${catalog.product} does not match ${registration.descriptor.id}"
        }
        catalog.releases.map(registration::releaseFrom)
    }

    private fun observeLatestVersions(registrations: List<RegisteredUpdate>, releases: List<ProductRelease>) {
        registrations.forEach { entry ->
            entry.observe(releases.filter { it.product == entry.descriptor.id }, configuredChannel(entry))
        }
    }

    private fun frozenProducts(registrations: List<RegisteredUpdate>): Set<ProductId> =
        (freezes.active().keys + registrations.mapNotNull { entry ->
                val policy = configuration.plugins[entry.descriptor.id.value]
                entry.descriptor.id.takeIf {
                    !configuration.pluginUpdatesEnabled || policy?.enabled == false ||
                        policy?.pauseUntil?.isAfter(java.time.Instant.now()) == true
                }
            }).toSet()

    private fun resolverPolicy(): ResolverPolicy = ResolverPolicy(
        configuration.downloads.allowManagedPlugins && configuration.installation.allowNewPlugins,
        runCatching { platform.installedPlugins() }.getOrNull().orEmpty().keys,
        )

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
        val plan = requireNotNull(snapshot.plan) { "update plan has no installable target" }
        verifyPluginDownloadsAllowed(plan)
        val installedTargets = entries.associate { registration ->
            registration.descriptor.id to InstalledUpdateTarget(
                currentJar = registration.jar,
                updateDirectory = registration.updateDirectory,
            )
        }
        planStager.stage(snapshot.id, plan, installedTargets)
    }

    private fun verifyPluginDownloadsAllowed(plan: UpdatePlan) {
        plan.changes
            .filterNot { change -> change.product.value == "pnlibrary" }
            .forEach { change ->
                val policy = configuration.plugins[change.product.value]
                require(configuration.pluginUpdatesEnabled && policy?.enabled != false) {
                    "Загрузка плагина ${change.product} отключена политикой обновлений"
                }
            }
    }

    private fun reconcilePendingUpdates(orchestrator: UpdateOrchestrator) {
        transaction.awaitingHealth().forEach { pending ->
            val installed = entries.associate { it.descriptor.id.value to it.descriptor.version.toString() }
            val missingOrWrong = pending.expectedVersions.filter { (product, version) -> installed[product] != version }
            val healthy = missingOrWrong.isEmpty()
            transaction.completeHealth(pending.journal, healthy)
            if (healthy) {
                orchestrator.healthResolved(true, "Обновлённые плагины загружены и работают.")
            } else {
                val details = missingOrWrong.entries.joinToString { (product, version) ->
                    "$product: ожидалась $version, загружена ${installed[product] ?: "не загружена"}"
                }
                orchestrator.healthResolved(
                    false,
                    "После перезапуска обновление не подтвердилось ($details). Доступен ручной откат.",
                )
            }
        }
    }

    private fun announce(snapshot: UpdatePlanSnapshot) {
        UpdateAnnouncementRenderer.render(snapshot, entries.map { it.snapshot })
            .forEach { platform.console(platform, it) }
    }

    private fun removeRegistration(registration: RegisteredUpdate) {
        entries.remove(registration)
        orchestrator.registrationsChanged()
    }

    private companion object {
        const val MAX_ARTIFACT_BYTES = 512L * 1024L * 1024L
    }
}
