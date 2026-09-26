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
import java.time.Duration
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletionStage
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

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
    private val http = TrustedHttpClient(Duration.ofSeconds(8), Duration.ofSeconds(20), configuration.downloads.allowedHosts)
    private val catalogue = ReleaseCatalogueClient(
        http, ReleaseCatalogueStore(dataFolder.resolve("updates/catalog")), Executor { it.run() }, Duration.ofMinutes(30),
    )
    private val transaction = UpdateTransaction(
        dataFolder.resolve("updates/transactions"), ArtifactVerifier(MAX_ARTIFACT_BYTES), dataFolder.parent,
    )
    private val freezes = FreezeStore(dataFolder.resolve("updates/freezes.json")).also { store ->
        configuration.components.forEach { (id, policy) ->
            policy.pause?.let { if (store.remaining(ProductId.of(id)) == null) store.freeze(ProductId.of(id), it) }
        }
    }
    private val orchestrator = UpdateOrchestrator(
        configuration, UpdateStateStore(dataFolder.resolve("updates"), warning = {
            platform.log(platform, LogLevel.WARNING, it)
        }), executor, ::resolveGraph, ::stageGraph, ::announce,
        automaticAllowed = ::automaticAllowed,
    ).also {
        it.start()
        runCatching { transaction.recoverAll() }.onFailure { error ->
            platform.log(platform, LogLevel.WARNING, "Update recovery failed", error)
        }
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
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(entriesLock) {
            entries.forEach(Registration::markClosed)
            entries.clear()
        }
        orchestrator.close()
    }

    private fun resolveGraph(): ResolutionResult {
        if (entries.isEmpty()) return ResolutionResult.Ready(UpdatePlan(PnLibraryApi.VERSION, emptyList(), emptyList()))
        val installed = entries.map { entry -> InstalledProduct(
            entry.descriptor.id, entry.descriptor.version, entry.descriptor.supportedApi,
            PnLibraryApi.VERSION.takeIf { entry.descriptor.id.value == "pnlibrary" },
        ) }
        val channels = entries.associate { entry -> entry.descriptor.id to
            (configuration.components[entry.descriptor.id.value]?.channel ?: entry.request.channel) }
        val releases = entries.flatMap { entry ->
            catalogue.releases(ReleaseSource(entry.request.repositoryOwner, entry.request.repositoryName),
                channels.getValue(entry.descriptor.id), entry.descriptor.id, entry.request, entry.dependencies, platform.type).join()
        }
        entries.forEach { entry ->
            val latest = releases.filter { it.product == entry.descriptor.id }.maxByOrNull(ProductRelease::version)
            entry.observe(latest)
        }
        return UpdateResolver(ProductId.of("pnlibrary")).resolve(
            installed, releases, channels = channels, defaultChannel = UpdateChannel.STABLE,
            frozen = freezes.active().keys, platform = platform.type,
            javaFeature = Runtime.version().feature(), policy = ResolverPolicy(
                configuration.downloads.allowManagedPlugins && configuration.installation.allowNewPlugins,
                runCatching { platform.installedPlugins() }.getOrNull().orEmpty().keys),
        )
    }

    private fun automaticAllowed(snapshot: UpdatePlanSnapshot): Boolean {
        val changed = snapshot.plan?.changes?.map(ProductChange::product).orEmpty()
        return changed.all { component ->
            configuration.components[component.value]?.automatic
                ?: entries.firstOrNull { it.descriptor.id == component }?.request?.automaticDownload
                ?: false
        }
    }

    private fun stageGraph(snapshot: UpdatePlanSnapshot) {
        val plan = requireNotNull(snapshot.plan) { "update plan has no installable target" }
        val staging = dataFolder.resolve("updates/staging/${snapshot.id}")
        val byComponent = entries.associateBy { it.descriptor.id }
        val artifacts = plan.changes.map { change ->
            val release = plan.selected.single { it.product == change.product }
            val descriptor = release.artifacts.firstOrNull {
                it.platform == platform.type && it.supports(Runtime.version().feature())
            } ?: error("No ${platform.type.id} artifact for ${change.product} ${change.to}")
            val uri = requireNotNull(descriptor.downloadUri) { "Release artifact has no verified download URL: ${descriptor.file}" }
            require(descriptor.size <= MAX_ARTIFACT_BYTES) { "Artifact exceeds the configured size limit: ${descriptor.file}" }
            val bytes = http.get(uri, descriptor.size.toInt())
            val source = staging.resolve(change.product.value).resolve(descriptor.file)
            ArtifactDownloader(MAX_ARTIFACT_BYTES).download({ ByteArrayInputStream(bytes) }, source)
            val specification = ArtifactSpecification(
                change.product, change.to, release.supportedApi, descriptor.file, descriptor.size, descriptor.sha256,
            )
            val existing = byComponent[change.product]
            val target = existing?.updateDir?.resolve(existing.jar.fileName)
                ?: dataFolder.parent.resolve("update").resolve(descriptor.file)
            TransactionArtifact(specification, source, target)
        }
        transaction.apply(artifacts) { true }
    }

    private fun announce(snapshot: UpdatePlanSnapshot) {
        val message = when (snapshot.state) {
            UpdateState.UPDATE_AVAILABLE -> "Доступен совместимый план обновления (${snapshot.plan?.changes?.size ?: 0} компонентов)"
            UpdateState.UPDATE_STAGED -> "План обновления проверен и подготовлен к перезапуску"
            UpdateState.BLOCKED -> "Обновление заблокировано: ${snapshot.blockers.joinToString()}"
            UpdateState.FAILED -> "Проверка обновлений завершилась ошибкой: ${snapshot.message}"
            else -> "Все зарегистрированные компоненты актуальны"
        }
        platform.log(platform, LogLevel.INFO, message)
    }

    private inner class Registration(
        private val owner: Any, val descriptor: ProductDescriptor, val request: PluginUpdateRequest,
        val dependencies: List<PluginDependency>,
        private val artifact: PluginUpdateArtifact, val jar: Path, val updateDir: Path,
    ) : UpdateRegistration {
        val product: String = descriptor.id.value
        val version: String = descriptor.version.toString()
        private val closed = AtomicBoolean(false)
        private val state = AtomicReference(UpdateSnapshot(
            product, version, null, request.channel, UpdateState.CHECKING, Runtime.version().feature(),
            artifact.minimumJava, request.automaticDownload, null, null,
        ))
        override val repository = "${request.repositoryOwner}/${request.repositoryName}"
        override val isClosed: Boolean get() = closed.get()
        override val snapshot get() = state.get()
        override fun checkNow() { check(!closed.get()); orchestrator.checkNow() }
        override fun downloadNow() { check(!closed.get()); orchestrator.checkNow().thenCompose { orchestrator.stage(it.id) } }
        fun observe(release: ProductRelease?) {
            if (closed.get()) return
            val latest = release?.version
            val current = SemanticVersion.parse(version)
            state.set(UpdateSnapshot(
                product, version, latest?.toString(), request.channel,
                if (latest != null && latest > current) UpdateState.AVAILABLE else UpdateState.CURRENT,
                Runtime.version().feature(), artifact.minimumJava, request.automaticDownload,
                "https://github.com/$repository/releases", null,
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
