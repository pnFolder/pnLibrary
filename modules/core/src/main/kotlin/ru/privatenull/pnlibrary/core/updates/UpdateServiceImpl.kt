package ru.privatenull.pnlibrary.core.updates

import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.updates.*
import ru.privatenull.pnlibrary.api.version.PnLibraryApi
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.update.ResolutionResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletionStage
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Single lifecycle owner for legacy registrations and graph update operations. */
internal class UpdateServiceImpl(private val platform: PlatformAdapter, dataFolder: Path) : UpdateService, AutoCloseable {
    private val entries = CopyOnWriteArrayList<Registration>()
    private val configuration = UpdateConfiguration.load(dataFolder.resolve("updates.yml")) {
        platform.log(platform, LogLevel.WARNING, "[pnLibrary] $it")
    }
    private val executor = Executors.newSingleThreadScheduledExecutor { action ->
        Thread(action, "pnLibrary-update-orchestrator").apply { isDaemon = true }
    }
    private val orchestrator = UpdateOrchestrator(
        configuration,
        UpdateStateStore(dataFolder.resolve("updates"), warning = { platform.log(platform, LogLevel.WARNING, "[pnLibrary] $it") }),
        executor, ::resolveGraph, ::stageGraph, ::announce,
    ).also(UpdateOrchestrator::start)

    override fun register(owner: Any, request: PluginUpdateRequest): UpdateRegistration {
        val info = platform.ownerDetails(owner)
        val product = info["name"] ?: request.repositoryName
        val version = info["version"] ?: error("Не удалось определить версию подключённого плагина")
        val jar = Paths.get(owner.javaClass.protectionDomain.codeSource.location.toURI()).toAbsolutePath().normalize()
        require(Files.isRegularFile(jar)) { "Плагин должен быть запущен из JAR" }
        val artifact = request.artifactFor(Runtime.version().feature())
            ?: error("Для Java ${Runtime.version().feature()} не зарегистрирован совместимый артефакт ${request.repositoryName}")
        return Registration(owner, product, version, request, artifact, jar, jar.parent.resolve("update")).also {
            entries += it
            orchestrator.checkNow()
        }
    }

    override fun registrations(): List<UpdateRegistration> = entries.toList()
    override fun find(product: String): UpdateRegistration? = entries.firstOrNull {
        it.product.equals(product, true) || it.request.repositoryName.equals(product, true)
    }
    override fun checkNow(): CompletionStage<UpdatePlanSnapshot> = orchestrator.checkNow()
    override fun currentPlan(): Optional<UpdatePlanSnapshot> = orchestrator.currentPlan()
    override fun stage(planId: UUID): CompletionStage<UpdatePlanSnapshot> = orchestrator.stage(planId)
    override fun history(): List<UpdatePlanSnapshot> = orchestrator.history()

    override fun close() {
        orchestrator.close()
        entries.forEach(Registration::markClosed)
        entries.clear()
    }

    private fun resolveGraph(): ResolutionResult {
        entries.forEach { it.refresh(false) }
        val releases = entries.map { entry ->
            val latest = entry.snapshot.latestVersion?.let(SemanticVersion::tryParse) ?: SemanticVersion.parse(entry.version)
            ComponentRelease(
                entry.request.component, latest, entry.request.channel, entry.request.supportedApi,
                PnLibraryApi.VERSION.takeIf { entry.request.component.value == "pnlibrary" },
                entry.request.dependencies, entry.repository,
            )
        }
        val changes = entries.mapNotNull { entry ->
            val from = SemanticVersion.parse(entry.version)
            val to = entry.snapshot.latestVersion?.let(SemanticVersion::tryParse) ?: return@mapNotNull null
            ComponentChange(entry.request.component, from, to).takeIf { to > from }
        }
        val blockers = entries.filterNot { it.request.supportedApi.supports(PnLibraryApi.VERSION) }.map {
            BlockedReason.ApiMismatch(it.request.component, it.request.supportedApi, PnLibraryApi.VERSION, it.repository)
        }
        val plan = UpdatePlan(PnLibraryApi.VERSION, changes, releases)
        return if (blockers.isEmpty()) ResolutionResult.Ready(plan) else ResolutionResult.Blocked(blockers, null)
    }

    private fun stageGraph(snapshot: UpdatePlanSnapshot) {
        val changed = snapshot.plan?.changes?.map(ComponentChange::component)?.toSet().orEmpty()
        entries.filter { it.request.component in changed }.forEach { it.refresh(true) }
        val failed = entries.filter { it.request.component in changed && it.snapshot.state == UpdateState.FAILED }
        check(failed.isEmpty()) { "Failed to stage: ${failed.joinToString { it.product }}" }
    }

    private fun announce(snapshot: UpdatePlanSnapshot) {
        val message = when (snapshot.state) {
            UpdateState.UPDATE_AVAILABLE -> "Доступен совместимый план обновления (${snapshot.plan?.changes?.size ?: 0} компонентов)"
            UpdateState.UPDATE_STAGED -> "План обновления проверен и подготовлен к перезапуску"
            UpdateState.BLOCKED -> "Обновление заблокировано: ${snapshot.blockers.size} несовместимых требований"
            UpdateState.FAILED -> "Проверка обновлений завершилась ошибкой: ${snapshot.message}"
            else -> "Все зарегистрированные компоненты актуальны"
        }
        platform.log(platform, LogLevel.INFO, "[pnLibrary] $message")
    }

    private inner class Registration(
        private val owner: Any, val product: String, val version: String, val request: PluginUpdateRequest,
        private val artifact: PluginUpdateArtifact, private val jar: Path, private val updateDir: Path,
    ) : UpdateRegistration {
        private val closed = AtomicBoolean(false)
        private val state = AtomicReference(UpdateSnapshot(
            product, version, null, request.channel, UpdateState.CHECKING, Runtime.version().feature(),
            artifact.minimumJava, request.automaticDownload, null, null,
        ))
        override val repository = "${request.repositoryOwner}/${request.repositoryName}"
        override val snapshot get() = state.get()
        override fun checkNow() { check(!closed.get()); orchestrator.checkNow() }
        override fun downloadNow() { check(!closed.get()); orchestrator.checkNow().thenCompose { orchestrator.stage(it.id) } }
        fun refresh(download: Boolean) {
            if (closed.get()) return
            runCatching {
                MandatoryUpdateService.checkOnce(
                    owner, platform, version, request.repositoryOwner, request.repositoryName,
                    request.channel.name.lowercase(), artifact.pattern, jar, updateDir, download, artifact.minimumJava,
                ) { if (!closed.get()) state.set(it) }
            }.onFailure { failure ->
                val previous = snapshot
                state.set(UpdateSnapshot(
                    product, version, previous.latestVersion, request.channel, UpdateState.FAILED,
                    Runtime.version().feature(), artifact.minimumJava, request.automaticDownload,
                    previous.releaseUrl, failure.message,
                ))
            }
        }
        override fun close() { if (closed.compareAndSet(false, true)) entries.remove(this) }
        fun markClosed() { closed.set(true) }
    }
}
