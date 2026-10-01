package ru.privatenull.pnlibrary.core.updates

import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.plugin.PluginDependency
import ru.privatenull.pnlibrary.api.updates.*
import ru.privatenull.pnlibrary.api.version.PnLibraryApi
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.update.*
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.net.URI
import java.time.Duration
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletionStage
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.security.MessageDigest

/** One catalogue → resolver → verifier → transaction pipeline for every component. */
internal class UpdateServiceImpl(private val platform: PlatformAdapter, private val dataFolder: Path) : UpdateService, AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val entries = CopyOnWriteArrayList<Registration>()
    private val entriesLock = Any()
    private val configuration = UpdateConfiguration.load(dataFolder.resolve("updates.yml")) {
        platform.log(platform, LogLevel.WARNING, it)
    }
    private val executor = Executors.newSingleThreadScheduledExecutor { action ->
        Thread(action, "pnLibrary-update-orchestrator").apply { isDaemon = true }
    }
    private val http = TrustedHttpClient(Duration.ofSeconds(8), Duration.ofSeconds(20))
    private val releaseCatalog = ReleaseCatalogClient(
        http, ReleaseCatalogueStore(dataFolder.resolve("updates/catalog")), Executor { it.run() }, Duration.ofMinutes(30),
    )
    private val transaction = UpdateTransaction(
        dataFolder.resolve("updates/transactions"), ArtifactVerifier(MAX_ARTIFACT_BYTES), dataFolder.parent,
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
        val registration = Registration(owner, product, request, dependencies.toList(), artifact, jar, jar.parent.resolve("update"))
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
            entries.forEach(Registration::markClosed)
            entries.clear()
        }
        orchestrator.close()
    }

    private fun resolveGraph(refreshMode: RefreshMode = RefreshMode.CACHED): ResolutionResult {
        val registrations = entries.toList()
        if (registrations.isEmpty()) return emptyUpdatePlan()

        val installed = installedProducts(registrations)
        val channels = updateChannels(registrations)
        val releases = requestReleaseCatalogs(registrations, channels, refreshMode)
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

    private fun installedProducts(registrations: List<Registration>): List<InstalledProduct> =
        registrations.map { entry -> InstalledProduct(
            entry.descriptor.id, entry.descriptor.version, entry.descriptor.supportedApi,
            PnLibraryApi.VERSION.takeIf { entry.descriptor.id.value == "pnlibrary" },
        ) }

    private fun updateChannels(registrations: List<Registration>): Map<ProductId, UpdateChannel> =
        registrations.associate { entry -> entry.descriptor.id to configuredChannel(entry) }

    private fun configuredChannel(entry: Registration): UpdateChannel =
        if (entry.descriptor.id.value == "pnlibrary") configuration.library.channel
        else configuration.plugins[entry.descriptor.id.value]?.channel ?: entry.request.channel

    private fun requestReleaseCatalogs(
        registrations: List<Registration>,
        channels: Map<ProductId, UpdateChannel>,
        refreshMode: RefreshMode,
    ): List<ProductRelease> = registrations.flatMap { entry ->
            // Use the raw endpoint directly. The github.com/raw URL adds several
            // redirects; when a server is slow those redirects multiply the HTTP
            // read timeout and make `/pn update` appear frozen for minutes.
            val source = URI.create("https://raw.githubusercontent.com/${entry.request.repositoryOwner}/${entry.request.repositoryName}/refs/heads/main/.pnlibrary/releases.json")
            val catalog = releaseCatalog.load(source, refreshMode).join()
            require(catalog.product.equals(entry.descriptor.id.value, ignoreCase = true)) {
                "Release catalog product ${catalog.product} does not match ${entry.descriptor.id}"
            }
            catalog.releases
                .asSequence()
                .map { release ->
                    ProductRelease(
                        entry.descriptor.id, release.version, release.channel, release.api,
                        providesApi = release.api.maximum.takeIf { entry.descriptor.id.value == "pnlibrary" },
                        repository = "${entry.request.repositoryOwner}/${entry.request.repositoryName}",
                        artifacts = release.artifacts.map { artifact ->
                            ArtifactDescriptor(
                                artifact.file, artifact.platform, artifact.javaMinimum, artifact.javaMaximum,
                                size = null, sha256 = null, downloadUri = artifact.url,
                            )
                        },
                        publishedAt = release.publishedAt,
                    )
                }.toList()
        }

    private fun observeLatestVersions(registrations: List<Registration>, releases: List<ProductRelease>) {
        registrations.forEach { entry ->
            entry.observe(releases.filter { it.product == entry.descriptor.id }, configuredChannel(entry))
        }
    }

    private fun frozenProducts(registrations: List<Registration>): Set<ProductId> =
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
        plan.changes.filter { it.product.value != "pnlibrary" }.forEach { change ->
            val policy = configuration.plugins[change.product.value]
            require(configuration.pluginUpdatesEnabled && policy?.enabled != false) {
                "Загрузка плагина ${change.product} отключена политикой обновлений"
            }
        }
        val staging = dataFolder.resolve("updates/staging/${snapshot.id}")
        val byComponent = entries.associateBy { it.descriptor.id }
        val artifacts = plan.changes.map { change ->
            val release = plan.selected.single { it.product == change.product }
            val descriptor = release.artifacts.firstOrNull {
                it.platform == platform.type && it.supports(Runtime.version().feature())
            } ?: error("No ${platform.type.id} artifact for ${change.product} ${change.to}")
            val uri = requireNotNull(descriptor.downloadUri) { "Release artifact has no verified download URL: ${descriptor.file}" }
            val bytes = http.get(uri, MAX_ARTIFACT_BYTES.toInt())
            val source = staging.resolve(change.product.value).resolve(descriptor.file)
            ArtifactDownloader(MAX_ARTIFACT_BYTES).download({ ByteArrayInputStream(bytes) }, source)
            val specification = ArtifactSpecification(
                change.product, change.to, release.supportedApi, descriptor.file,
                bytes.size.toLong(), sha256(bytes),
            )
            val existing = byComponent[change.product]
            val target = existing?.updateDir?.resolve(existing.jar.fileName)
                ?: dataFolder.parent.resolve("update").resolve(descriptor.file)
            TransactionArtifact(specification, source, target, rollbackSource = existing?.jar ?: target)
        }
        transaction.prepareForRestart(artifacts)
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
        UpdateAnnouncementRenderer.render(snapshot)
            .forEach { platform.console(platform, it) }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private inner class Registration(
        private val owner: Any, val descriptor: ProductDescriptor, val request: PluginUpdateRequest,
        val dependencies: List<PluginDependency>,
        private val artifact: PluginUpdateArtifact, val jar: Path, val updateDir: Path,
    ) : UpdateRegistration {
        val product: String = descriptor.id.value
        val version: String = descriptor.version.toString()
        private val closed = AtomicBoolean(false)
        private val state = AtomicReference(UpdateSnapshot(
            product, version, null,
            if (descriptor.id.value == "pnlibrary") configuration.library.channel else request.channel,
            UpdateState.CHECKING, Runtime.version().feature(),
            artifact.minimumJava, request.automaticDownload, null, null, request.supportedApi,
        ))
        override val repository = "${request.repositoryOwner}/${request.repositoryName}"
        override val isClosed: Boolean get() = closed.get()
        override val snapshot get() = state.get()
        override fun checkNow() { check(!closed.get()); orchestrator.checkNow() }
        override fun downloadNow() { check(!closed.get()); orchestrator.checkNow().thenCompose { orchestrator.stage(it.id) } }
        fun observe(releases: List<ProductRelease>, selectedChannel: UpdateChannel) {
            if (closed.get()) return
            val productReleases = releases.filter { it.product == descriptor.id }
            val allowed = productReleases.filter { request.channel.accepts(it.channel) }
            val latest = allowed.maxByOrNull(ProductRelease::version)
            val current = SemanticVersion.parse(version)
            state.set(UpdateSnapshot(
                product, version, latest?.version?.toString(), selectedChannel,
                if (latest != null && latest.version > current) UpdateState.AVAILABLE else UpdateState.CURRENT,
                Runtime.version().feature(), artifact.minimumJava, request.automaticDownload,
                "https://github.com/$repository/releases", null, request.supportedApi,
                productReleases.sortedByDescending(ProductRelease::version).map {
                    ReleaseSummary(it.version.toString(), it.channel, it.publishedAt)
                },
            ))
        }
        override fun close() {
            if (closed.compareAndSet(false, true)) {
                entries.remove(this)
                orchestrator.registrationsChanged()
            }
        }
        fun markClosed() { closed.set(true) }
    }

    private companion object { const val MAX_ARTIFACT_BYTES = 512L * 1024L * 1024L }
}
