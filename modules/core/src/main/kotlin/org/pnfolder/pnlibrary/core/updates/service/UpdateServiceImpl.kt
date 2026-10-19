package org.pnfolder.pnlibrary.core.updates.service

import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.plugin.PluginDependency
import org.pnfolder.pnlibrary.api.updates.PluginUpdateRequest
import org.pnfolder.pnlibrary.api.updates.ProductDescriptor
import org.pnfolder.pnlibrary.api.updates.UpdateActor
import org.pnfolder.pnlibrary.api.updates.UpdateBackup
import org.pnfolder.pnlibrary.api.updates.UpdateChannel
import org.pnfolder.pnlibrary.api.updates.UpdateHistoryEntry
import org.pnfolder.pnlibrary.api.updates.UpdatePlanSnapshot
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration
import org.pnfolder.pnlibrary.api.updates.UpdateService
import org.pnfolder.pnlibrary.api.updates.UpdateSnapshot
import org.pnfolder.pnlibrary.api.version.SemanticVersion
import org.pnfolder.pnlibrary.common.jvm.JavaRuntime
import org.pnfolder.pnlibrary.core.updates.catalog.UpdateAnnouncementRenderer
import org.pnfolder.pnlibrary.core.updates.engine.FreezeStore
import org.pnfolder.pnlibrary.core.updates.engine.RefreshMode
import org.pnfolder.pnlibrary.core.updates.engine.ResolutionResult
import org.pnfolder.pnlibrary.core.updates.install.UpdateGraphResolver
import org.pnfolder.pnlibrary.core.updates.install.UpdateInstaller
import org.pnfolder.pnlibrary.core.updates.policy.UpdateAutomaticPolicy
import org.pnfolder.pnlibrary.core.updates.policy.UpdateConfiguration
import org.pnfolder.pnlibrary.core.updates.policy.UpdatePolicyOverrides
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnerMetadata
import org.pnfolder.pnlibrary.spi.platform.PlatformPluginInventory
import org.pnfolder.pnlibrary.spi.platform.PlatformReadiness
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.Locale
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean

/** One catalog → resolver → verifier → transaction pipeline for every component. */
internal class UpdateServiceImpl(
    private val logging: PlatformLogging,
    private val ownership: PlatformOwnerMetadata,
    private val inventory: PlatformPluginInventory,
    private val identity: PlatformIdentity,
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
    private val overrides = UpdatePolicyOverrides(dataFolder.resolve("updates/settings.json")) {
        logging.log(this, LogLevel.WARNING, it)
    }
    private val automaticPolicy = UpdateAutomaticPolicy(configuration, overrides) { registrations.snapshot() }
    private val installer = UpdateInstaller(identity, dataFolder, configuration, resources.http)
    private val freezes = FreezeStore(dataFolder.resolve("updates/freezes.json"))
    private val graphResolver = UpdateGraphResolver(inventory, identity, configuration, resources.catalogGateway, freezes, overrides)
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
        automaticAllowed = { snapshot ->
            automaticPolicy.allows(snapshot) && installer.automaticUpdatesHealthy(registrations.snapshot().map { it.descriptor.id }.toSet())
        },
        automaticOverridesActive = { configuration.enabled && overrides.anyAutomaticEnabled() },
    )
    private val orchestrator get() = runtime.orchestrator

    override fun register(
        owner: Any,
        product: ProductDescriptor,
        request: PluginUpdateRequest,
        dependencies: List<PluginDependency>,
    ): UpdateRegistration {
        check(!closed.get()) { "Update service is closed" }
        val info = ownership.ownerDetails(owner)
        val nativeProductName = info["name"] ?: request.repositoryName
        val version = info["version"] ?: error("Cannot determine the version of the registering plugin")
        require(SemanticVersion.tryParse(version) != null) {
            "Version of $nativeProductName must be semantic: $version"
        }
        val jar = Paths.get(owner.javaClass.protectionDomain.codeSource.location.toURI()).toAbsolutePath().normalize()
        require(Files.isRegularFile(jar)) { "Plugin must be loaded from a JAR file" }
        request.configurationBackup?.let { backup ->
            require(backup.root != jar.parent && backup.root.startsWith(jar.parent)) {
                "Configuration backup root must be a component data directory inside the plugins directory"
            }
        }
        val artifact = request.artifactFor(JavaRuntime.feature, identity.type)
            ?: error("No artifact of ${request.repositoryName} is registered for Java ${JavaRuntime.feature}")
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
            effectiveAutomatic = {
                overrides.automatic(product.id.value) ?: request.automaticDownload
            },
        )
        check(!closed.get()) { "Update service is closed" }
        registrations.add(registration)
        orchestrator.registrationsChanged()
        return registration
    }

    override fun all(): List<UpdateRegistration> = registrations.publicSnapshot()
    override fun get(product: String): UpdateRegistration? = registrations.find(product)
    override fun checkNow(): CompletionStage<UpdatePlanSnapshot> = orchestrator.checkNow()
    override fun checkNow(actor: UpdateActor): CompletionStage<UpdatePlanSnapshot> = orchestrator.checkNow(actor = actor)
    override fun currentPlan(): Optional<UpdatePlanSnapshot> = orchestrator.currentPlan()
    override fun stage(planId: UUID): CompletionStage<UpdatePlanSnapshot> = orchestrator.stage(planId)
    override fun stage(planId: UUID, actor: UpdateActor): CompletionStage<UpdatePlanSnapshot> = orchestrator.stage(planId, actor)
    override fun history(): List<UpdatePlanSnapshot> = orchestrator.history()
    override fun historyPage(page: Int, product: String?): CompletionStage<List<UpdateHistoryEntry>> = runtime.historyPage(page, product)
    override fun historyCount(product: String?): CompletionStage<Int> = runtime.historyCount(product)
    override fun setChannel(product: String, channel: UpdateChannel, actor: UpdateActor): CompletionStage<UpdateSnapshot> =
        changeSetting(product, actor, recheck = true) { registration ->
            val before = registration.snapshot.channel
            if (before == channel) null
            else {
                overrides.setChannel(registration.product, channel)
                "канал: ${before.name.lowercase()} → ${channel.name.lowercase()}"
            }
        }

    override fun setAutomaticDownload(product: String, enabled: Boolean, actor: UpdateActor): CompletionStage<UpdateSnapshot> =
        changeSetting(product, actor, recheck = false) { registration ->
            val before = registration.snapshot.automaticDownload
            if (before == enabled) null
            else {
                overrides.setAutomatic(registration.product, enabled)
                "автозагрузка: ${if (before) "вкл" else "выкл"} → ${if (enabled) "вкл" else "выкл"}"
            }
        }

    override fun setHeld(product: String, duration: Duration?, actor: UpdateActor): CompletionStage<UpdateSnapshot> =
        changeSetting(product, actor, recheck = true) { registration ->
            val id = registration.descriptor.id
            if (duration == null) {
                if (freezes.clear(id)) "удержание обновлений снято" else null
            } else {
                val until = freezes.freeze(id, duration)
                "обновления удержаны до ${HOLD_FORMAT.format(until.atZone(ZoneId.systemDefault()))}"
            }
        }

    override fun heldUntil(product: String): Instant? =
        registrations.find(product)?.let { freezes.active()[it.descriptor.id] }

    /** Applies one administrator setting, records it with its author and refreshes what readers see. */
    private fun changeSetting(
        product: String,
        actor: UpdateActor,
        recheck: Boolean,
        change: (RegisteredUpdate) -> String?,
    ): CompletionStage<UpdateSnapshot> {
        val result = CompletableFuture<UpdateSnapshot>()
        try {
            check(!closed.get()) { "Update service is closed" }
            val registration = registrations.find(product)
                ?: throw IllegalArgumentException("Плагин $product не зарегистрирован в pnLibrary")
            val description = change(registration)
            registration.refreshPolicy()
            if (description != null) {
                runtime.recordSettingsChange(registration, description, actor)
                // The setting is already persisted, so a failed recheck is reported but does not fail the change.
                if (recheck) runCatching { orchestrator.checkNow(actor = actor) }.onFailure { error ->
                    logging.log(this, LogLevel.WARNING, "Update recheck after a settings change failed", error)
                }
            }
            result.complete(registration.snapshot)
        } catch (error: Exception) {
            result.completeExceptionally(error)
        }
        return result
    }

    override fun backups(): CompletionStage<List<UpdateBackup>> = runtime.readAsync {
        Collections.unmodifiableList(installer.backups())
    }
    override fun rollback(): CompletionStage<UpdatePlanSnapshot> = rollback(UpdateActor.UNKNOWN)
    override fun rollback(actor: UpdateActor): CompletionStage<UpdatePlanSnapshot> = orchestrator.rollbackTo(actor) {
        installer.rollbackLatest()
    }
    override fun rollback(transactionId: UUID, actor: UpdateActor): CompletionStage<UpdatePlanSnapshot> =
        orchestrator.rollbackTo(actor, transactionId) { installer.rollback(transactionId) }
    override fun rollback(transactionId: UUID, restoreConfigurations: Boolean, actor: UpdateActor): CompletionStage<UpdatePlanSnapshot> =
        orchestrator.rollbackTo(actor, transactionId) { installer.rollback(transactionId, restoreConfigurations) }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        registrations.close()
        runtime.close()
        resources.close()
    }

    private companion object {
        val HOLD_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale("ru"))
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
