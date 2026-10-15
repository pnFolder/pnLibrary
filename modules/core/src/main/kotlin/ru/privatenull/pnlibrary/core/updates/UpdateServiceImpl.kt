package ru.privatenull.pnlibrary.core.updates

import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.plugin.PluginDependency
import ru.privatenull.pnlibrary.api.updates.*
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.spi.platform.PlatformExecution
import ru.privatenull.pnlibrary.spi.platform.PlatformReadiness
import ru.privatenull.pnlibrary.spi.platform.PlatformIdentity
import ru.privatenull.pnlibrary.spi.platform.PlatformLogging
import ru.privatenull.pnlibrary.spi.platform.PlatformOwnerMetadata
import ru.privatenull.pnlibrary.spi.platform.PlatformPluginInventory
import ru.privatenull.pnlibrary.update.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean

/** One catalogue → resolver → verifier → transaction pipeline for every component. */
internal class UpdateServiceImpl(
    private val logging: PlatformLogging,
    private val ownership: PlatformOwnerMetadata,
    private val inventory: PlatformPluginInventory,
    private val identity: PlatformIdentity,
    private val execution: PlatformExecution,
    private val readiness: PlatformReadiness,
    private val dataFolder: Path,
) : UpdateService, AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val registrations = UpdateRegistrationStore()
    private val configuration = UpdateConfiguration.load(dataFolder.resolve("updates.yml")) {
        logging.log(this, LogLevel.WARNING, it)
    }
    // Keep a manual `/pn update` responsive even when GitHub is unreachable.
    // The check can cover several registered products, so long per-request
    // timeouts otherwise add up and look like a frozen command.
    private val resources = UpdateRuntimeResources(dataFolder)
    private val automaticPolicy = UpdateAutomaticPolicy(configuration) { registrations.snapshot() }
    private val installer = UpdateInstaller(identity, dataFolder, configuration, resources.http)
    private val freezes = FreezeStore(dataFolder.resolve("updates/freezes.json"))
    private val graphResolver = UpdateGraphResolver(ownership, inventory, identity, configuration, resources.catalogGateway, freezes)
    private val runtime = UpdateOrchestratorRuntime(
        configuration = configuration,
        dataFolder = dataFolder,
        logging = logging,
        readiness = readiness,
        installer = installer,
        registrations = registrations::snapshot,
        resolveGraph = ::resolveGraph,
        stageGraph = ::stageGraph,
        announce = ::announce,
        automaticAllowed = automaticPolicy::allows,
    )
    private val orchestrator get() = runtime.orchestrator

    override fun register(
        owner: Any,
        product: ProductDescriptor,
        request: PluginUpdateRequest,
        dependencies: List<PluginDependency>,
    ): UpdateRegistration {
        check(!closed.get()) { "update service is closed" }
        val info = ownership.ownerDetails(owner)
        val nativeProductName = info["name"] ?: request.repositoryName
        val version = info["version"] ?: error("Не удалось определить версию подключённого плагина")
        require(SemanticVersion.tryParse(version) != null) {
            "Version of $nativeProductName must be semantic: $version"
        }
        val jar = Paths.get(owner.javaClass.protectionDomain.codeSource.location.toURI()).toAbsolutePath().normalize()
        require(Files.isRegularFile(jar)) { "Плагин должен быть запущен из JAR" }
        val artifact = request.artifactFor(Runtime.version().feature(), identity.type)
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
        check(!closed.get()) { "update service is closed" }
        registrations.add(registration)
        orchestrator.registrationsChanged()
        return registration
    }

    override fun all(): List<UpdateRegistration> = registrations.publicSnapshot()
    override fun get(product: String): UpdateRegistration? = registrations.find(product)
    override fun checkNow(): CompletionStage<UpdatePlanSnapshot> = orchestrator.checkNow()
    override fun currentPlan(): Optional<UpdatePlanSnapshot> = orchestrator.currentPlan()
    override fun stage(planId: UUID): CompletionStage<UpdatePlanSnapshot> = orchestrator.stage(planId)
    override fun history(): List<UpdatePlanSnapshot> = orchestrator.history()
    override fun rollback(): CompletionStage<UpdatePlanSnapshot> = orchestrator.rollback {
        installer.rollbackLatest()
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        registrations.close()
        runtime.close()
        resources.close()
    }

    private fun resolveGraph(refreshMode: RefreshMode = RefreshMode.CACHED): ResolutionResult =
        graphResolver.resolve(registrations.snapshot(), refreshMode)

    private fun stageGraph(snapshot: UpdatePlanSnapshot) {
        installer.stage(snapshot, registrations.snapshot())
    }

    private fun announce(snapshot: UpdatePlanSnapshot) {
        UpdateAnnouncementRenderer.render(snapshot, registrations.snapshot().map { it.snapshot })
            .forEach { logging.console(this, it) }
    }

    private fun removeRegistration(registration: RegisteredUpdate) {
        registrations.remove(registration)
        orchestrator.registrationsChanged()
    }
}
