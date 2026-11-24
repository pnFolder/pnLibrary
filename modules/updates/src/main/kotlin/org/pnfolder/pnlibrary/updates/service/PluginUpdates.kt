package org.pnfolder.pnlibrary.updates.service

import org.pnfolder.pnlibrary.api.dependencies.PluginDependency
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.api.updates.UpdateBackups
import org.pnfolder.pnlibrary.api.updates.UpdateHistory
import org.pnfolder.pnlibrary.api.updates.UpdatePlans
import org.pnfolder.pnlibrary.api.updates.UpdateProducts
import org.pnfolder.pnlibrary.api.updates.UpdateRegistration
import org.pnfolder.pnlibrary.api.updates.UpdateService
import org.pnfolder.pnlibrary.api.updates.UpdateSettings
import org.pnfolder.pnlibrary.api.updates.backup.UpdateBackup
import org.pnfolder.pnlibrary.api.updates.history.UpdateHistoryEntry
import org.pnfolder.pnlibrary.api.updates.product.ProductDescriptor
import org.pnfolder.pnlibrary.api.updates.product.UpdateChannel
import org.pnfolder.pnlibrary.api.updates.request.PluginUpdateRequest
import org.pnfolder.pnlibrary.api.updates.state.UpdateActor
import org.pnfolder.pnlibrary.api.updates.state.UpdatePlanSnapshot
import org.pnfolder.pnlibrary.api.updates.state.UpdateSnapshot
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.console.ConsoleTheme
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnerMetadata
import org.pnfolder.pnlibrary.spi.platform.PlatformPluginInventory
import org.pnfolder.pnlibrary.spi.platform.PlatformReadiness
import org.pnfolder.pnlibrary.updates.databases.DatabaseBackups
import org.pnfolder.pnlibrary.updates.databases.DatabaseSources
import org.pnfolder.pnlibrary.updates.engine.FreezeStore
import org.pnfolder.pnlibrary.updates.engine.ResolutionResult
import org.pnfolder.pnlibrary.updates.engine.UpdatePreferencesStore
import org.pnfolder.pnlibrary.updates.engine.catalog.RefreshMode
import org.pnfolder.pnlibrary.updates.install.UpdateGraphResolver
import org.pnfolder.pnlibrary.updates.install.UpdateInstaller
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths
import org.pnfolder.pnlibrary.updates.policy.UpdateAutomaticPolicy
import org.pnfolder.pnlibrary.updates.policy.UpdateConfiguration
import org.pnfolder.pnlibrary.updates.policy.UpdatePolicyOverrides
import org.pnfolder.pnlibrary.updates.policy.UpdateTiming
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Collections
import java.util.Locale
import java.util.Optional
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicBoolean

/** One catalog → resolver → verifier → transaction pipeline for every component. */
class PluginUpdates(
    private val logging: PlatformLogging,
    private val ownership: PlatformOwnerMetadata,
    private val inventory: PlatformPluginInventory,
    private val identity: PlatformIdentity,
    private val readiness: PlatformReadiness,
    private val paths: LibraryPaths,
    private val consoleTheme: ConsoleTheme,
    timing: UpdateTiming = UpdateTiming(),
) : UpdateService, AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val registrations = UpdateRegistrationStore()
    private val configuration = UpdateConfiguration(
        checks = UpdateConfiguration.Checks(timing.checkInterval),
        downloads = UpdateConfiguration.Downloads(connectTimeout = timing.connectTimeout, readTimeout = timing.readTimeout),
    )
    private val registrationValidator = RegistrationValidator(ownership, identity)
    init {
        retireOldSettingsFile()
    }
    private val resources = UpdateRuntimeResources(paths.catalog, configuration)
    private val preferences = UpdatePreferencesStore(paths.preferences) { logging.log(this, LogLevel.WARNING, it) }
    private val overrides = UpdatePolicyOverrides(preferences) {
        logging.log(this, LogLevel.WARNING, it)
    }
    private val automaticPolicy = UpdateAutomaticPolicy(configuration, overrides) { registrations.snapshot() }
    /** Where plugins say their databases are; the configuration service fills it as files are registered. */
    val databaseSources = DatabaseSources()
    private val installer = UpdateInstaller(
        identity, paths, configuration, resources.downloadHttp,
        DatabaseBackups(paths.databases, databaseSources),
        javaChoice = { product -> overrides.javaBuild(product.value) },
        installedJava = { product -> registrations.find(product.value)?.descriptor?.minimumJava },
    ) {
        logging.log(this, LogLevel.WARNING, it)
    }
    private val freezes = FreezeStore(preferences)
    private val graphResolver = UpdateGraphResolver(inventory, identity, configuration, resources.catalogGateway, freezes, overrides) { message, error ->
        logging.log(this, LogLevel.ERROR, message, error)
    }
    private val runtime = UpdateOrchestratorRuntime(
        configuration = configuration,
        paths = paths,
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
        automaticOverridesActive = { overrides.anyAutomaticEnabled() },
    )
    private val orchestrator get() = runtime.orchestrator

    /** The settings file of older versions no longer does anything; it is kept aside so nothing is lost. */
    private fun retireOldSettingsFile() {
        val old = paths.pluginFolder.resolve("updates.yml")
        if (!Files.isRegularFile(old)) return
        val kept = paths.pluginFolder.resolve("updates.yml.old")
        Files.move(old, kept, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        logging.log(
            this, LogLevel.WARNING,
            "updates.yml больше не используется: обновления настраиваются командой /pn update и в меню. Файл сохранён как ${kept.fileName}.",
        )
    }

    override fun register(
        owner: Any,
        product: ProductDescriptor,
        request: PluginUpdateRequest,
        dependencies: List<PluginDependency>,
    ): UpdateRegistration {
        check(!closed.get()) { "Update service is closed" }
        val (jar, pluginsDirectory, artifact) = registrationValidator.validate(owner, product, request)
        val registration = RegisteredUpdate(
            descriptor = product,
            request = request,
            artifact = artifact,
            jar = jar,
            updateDirectory = pluginsDirectory.resolve("update"),
            selectedChannel = { graphResolver.channelFor(product.id, request.channel) },
            checkForUpdates = orchestrator::checkNow,
            stagePlan = { snapshot -> orchestrator.stage(snapshot.id) },
            onClose = ::removeRegistration,
            effectiveAutomatic = {
                overrides.automatic(product.id.value) ?: (request.automaticDownload && product.id.value != PnLibraryIdentity.PRODUCT_ID)
            },
            platform = identity.type,
            chosenJava = { overrides.javaBuild(product.id.value) },
        )
        check(!closed.get()) { "Update service is closed" }
        registrations.add(registration)
        orchestrator.registrationsChanged()
        return registration
    }

    override val products: UpdateProducts = object : UpdateProducts {
        override fun all(): List<UpdateRegistration> = registrations.publicSnapshot()
        override fun get(product: String): UpdateRegistration? = registrations.find(product)
    }

    override val plans: UpdatePlans = object : UpdatePlans {
        override fun check(actor: UpdateActor): CompletionStage<UpdatePlanSnapshot> = orchestrator.checkNow(actor = actor)
        override fun current(): Optional<UpdatePlanSnapshot> = orchestrator.currentPlan()
        override fun recent(): List<UpdatePlanSnapshot> = orchestrator.history()
        override fun stage(planId: UUID, actor: UpdateActor): CompletionStage<UpdatePlanSnapshot> = orchestrator.stage(planId, actor)
    }

    override val history: UpdateHistory = object : UpdateHistory {
        override fun page(page: Int, product: String?): CompletionStage<List<UpdateHistoryEntry>> = runtime.historyPage(page, product)
        override fun count(product: String?): CompletionStage<Int> = runtime.historyCount(product)
    }

    override val settings: UpdateSettings = object : UpdateSettings {
        override fun channel(product: String, channel: UpdateChannel?, actor: UpdateActor): CompletionStage<UpdateSnapshot> =
            changeSetting(product, actor, recheck = true) { registration ->
                val before = registration.snapshot.channel
                val chosen = overrides.channel(registration.product)
                when {
                    chosen == channel -> null
                    channel == null -> {
                        overrides.setChannel(registration.product, null)
                        val after = graphResolver.channelFor(registration)
                        "выбор канала снят: ${before.name.lowercase()} → ${after.name.lowercase()}, канал определяет плагин"
                    }
                    else -> {
                        overrides.setChannel(registration.product, channel)
                        "канал: ${before.name.lowercase()} → ${channel.name.lowercase()}"
                    }
                }
            }

        override fun javaBuild(product: String, java: Int?, actor: UpdateActor): CompletionStage<UpdateSnapshot> =
            changeSetting(product, actor, recheck = false) { registration ->
                val available = registration.snapshot.javaBuilds
                require(java == null || available.isEmpty() || java in available) {
                    "Для ${registration.product} нет сборки под Java $java. Есть: ${available.joinToString()}"
                }
                val before = overrides.javaBuild(registration.product)
                if (before == java) null
                else {
                    overrides.setJavaBuild(registration.product, java)
                    if (java == null) "сборка Java: выбор снят, подбирается подходящая" else "сборка Java: ${before ?: "подбор"} → $java"
                }
            }

        override fun automaticDownload(product: String, enabled: Boolean, actor: UpdateActor): CompletionStage<UpdateSnapshot> =
            changeSetting(product, actor, recheck = false) { registration ->
                val before = registration.snapshot.automaticDownload
                if (before == enabled) null
                else {
                    overrides.setAutomatic(registration.product, enabled)
                    "автозагрузка: ${if (before) "вкл" else "выкл"} → ${if (enabled) "вкл" else "выкл"}"
                }
            }

        override fun automaticDownloadForAll(enabled: Boolean, actor: UpdateActor): CompletionStage<List<UpdateSnapshot>> {
            overrides.setAllPlugins(enabled, PnLibraryIdentity.PRODUCT_ID)
            val state = if (enabled) "включена" else "выключена"
            logging.log(this@PluginUpdates, LogLevel.INFO, "Автозагрузка обновлений плагинов: $state для всех (${actor.name})")
            return CompletableFuture.completedFuture(registrations.snapshot().map { it.snapshot })
        }

        override fun hold(product: String, duration: Duration, actor: UpdateActor): CompletionStage<UpdateSnapshot> =
            changeSetting(product, actor, recheck = true) { registration ->
                val until = freezes.freeze(registration.descriptor.id, duration)
                "обновления удержаны до ${HOLD_FORMAT.format(until.atZone(org.pnfolder.pnlibrary.common.time.DisplayZone.zone))}"
            }

        override fun release(product: String, actor: UpdateActor): CompletionStage<UpdateSnapshot> =
            changeSetting(product, actor, recheck = true) { registration ->
                if (freezes.clear(registration.descriptor.id)) "удержание обновлений снято" else null
            }

        override fun heldUntil(product: String): Instant? =
            registrations.find(product)?.let { freezes.active()[it.descriptor.id] }
    }

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
                if (recheck) runCatchingNonFatal { orchestrator.checkNow(actor = actor) }.onFailure { error ->
                    logging.log(this, LogLevel.ERROR, "Update recheck after a settings change failed", error)
                }
            }
            result.complete(registration.snapshot)
        } catch (error: Exception) {
            result.completeExceptionally(error)
        }
        return result
    }

    override val backups: UpdateBackups = object : UpdateBackups {
        override fun list(): CompletionStage<List<UpdateBackup>> = runtime.readAsync {
            Collections.unmodifiableList(installer.backups())
        }

        override fun rollback(actor: UpdateActor): CompletionStage<UpdatePlanSnapshot> =
            orchestrator.rollbackTo(actor) { installer.rollbackLatest() }

        override fun rollback(transactionId: UUID, actor: UpdateActor, restoreConfigurations: Boolean): CompletionStage<UpdatePlanSnapshot> =
            orchestrator.rollbackTo(actor, transactionId) { installer.rollback(transactionId, restoreConfigurations) }

        override fun delete(transactionId: UUID, actor: UpdateActor): CompletionStage<Void?> = runtime.readAsync {
            installer.deleteBackup(transactionId)
            logging.log(this@PluginUpdates, LogLevel.INFO, "Резервный набор $transactionId удалён (${actor.name})")
            null
        }

        override fun restoreDatabase(transactionId: UUID, product: String, name: String, actor: UpdateActor): CompletionStage<Void?> =
            runtime.readAsync {
                installer.restoreDatabase(transactionId, product, name)
                logging.log(this@PluginUpdates, LogLevel.WARNING, "База «$name» плагина $product возвращена из набора $transactionId (${actor.name})")
                null
            }

        override fun extract(transactionId: UUID, product: String, actor: UpdateActor): CompletionStage<String> = runtime.readAsync {
            val folder = installer.extractBackup(transactionId, product).toAbsolutePath().toString()
            logging.log(this@PluginUpdates, LogLevel.INFO, "Файлы плагина $product из набора $transactionId выданы в $folder (${actor.name})")
            folder
        }
    }

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
        UpdateAnnouncementRenderer.render(snapshot, consoleTheme, registrations.snapshot().map { it.snapshot })
            .forEach { logging.console(this, it) }
    }

    private fun removeRegistration(registration: RegisteredUpdate) {
        registrations.remove(registration)
        orchestrator.registrationsChanged()
    }
}
