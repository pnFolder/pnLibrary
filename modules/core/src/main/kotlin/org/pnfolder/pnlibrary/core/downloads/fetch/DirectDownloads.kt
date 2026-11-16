package org.pnfolder.pnlibrary.core.downloads.fetch

import org.pnfolder.pnlibrary.api.dependencies.ExternalPluginDependency
import org.pnfolder.pnlibrary.api.dependencies.PluginDependency
import org.pnfolder.pnlibrary.api.downloads.DownloadRegistration
import org.pnfolder.pnlibrary.api.downloads.DownloadSnapshot
import org.pnfolder.pnlibrary.api.downloads.FileDownload
import org.pnfolder.pnlibrary.api.downloads.FileDownloads
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.platform.PlatformType
import org.pnfolder.pnlibrary.api.runtime.PnLibraryIdentity
import org.pnfolder.pnlibrary.common.lang.runCatchingNonFatal
import org.pnfolder.pnlibrary.core.downloads.DownloadBatch
import org.pnfolder.pnlibrary.core.downloads.dependencies.DependencyDownloadReporter
import org.pnfolder.pnlibrary.core.downloads.dependencies.DependencyDownloadRequestFactory
import org.pnfolder.pnlibrary.core.downloads.dependencies.DownloadedDependencyStore
import org.pnfolder.pnlibrary.core.downloads.dependencies.MissingDependencies
import org.pnfolder.pnlibrary.core.downloads.dependencies.MissingDependency
import org.pnfolder.pnlibrary.core.downloads.dependencies.MissingGroup
import org.pnfolder.pnlibrary.core.downloads.settings.DownloadConfiguration
import org.pnfolder.pnlibrary.core.downloads.settings.DownloadPins
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnerMetadata
import org.pnfolder.pnlibrary.spi.platform.PlatformPluginInventory
import org.pnfolder.pnlibrary.spi.platform.PlatformReadiness
import org.pnfolder.pnlibrary.updates.engine.net.TrustedHttpClient
import org.pnfolder.pnlibrary.updates.paths.LibraryPaths
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletionStage
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Downloads, verifies and atomically publishes files declared by plugins, including missing plugin dependencies. */
internal class DirectDownloads(
    private val identity: PlatformIdentity,
    private val ownership: PlatformOwnerMetadata,
    private val inventory: PlatformPluginInventory,
    private val readiness: PlatformReadiness,
    private val logging: PlatformLogging,
    paths: LibraryPaths,
    private val configuration: () -> DownloadConfiguration,
    private val saveAutomatic: (Boolean) -> Unit = {},
    private val http: TrustedHttpClient = TrustedHttpClient(
        Duration.ofSeconds(8), Duration.ofSeconds(30),
    ),
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { action ->
        Thread(action, "pnLibrary-direct-downloads").apply { isDaemon = true }
    }
    private val registrations = CopyOnWriteArrayList<DownloadBatch>()
    private val pluginJarVerifier = PluginJarVerifier(identity)
    private val filePublisher = AtomicDownloadPublisher(paths.data)
    private val pins = DownloadPins(paths.data)
    private val missing = MissingDependencies()
    private val artifactPreparer = DownloadArtifactPreparer(
        identity.type, paths.data, configuration, http, pins, MAX_BYTES,
    )
    private val dependencyStore = DownloadedDependencyStore(paths.data)
    private val dependencyReporter = DependencyDownloadReporter(
        logging, ownership, if (identity.type == PlatformType.VELOCITY || identity.type == PlatformType.BUNGEECORD) "pnproxy" else "pn",
    )
    private val dependencyRequestFactory = DependencyDownloadRequestFactory(
        paths.serverUpdateFolder,
    )

    fun register(owner: Any, request: FileDownloads): DownloadRegistration = register(owner, request, null)

    private fun register(
        owner: Any,
        request: FileDownloads,
        verifier: ((FileDownload, Path) -> Unit)?,
    ): DownloadRegistration = DownloadBatch(
        request = request,
        configuration = configuration,
        executor = executor,
        managerIsClosed = closed::get,
        install = { declarations, beforePublish ->
            installBatch(request, declarations, beforePublish, verifier)
            val dependencyNames = declarations.dependencyNames()
            if (dependencyNames.isNotEmpty()) dependencyStore.record(dependencyNames)
        },
        blocked = { reason -> logging.log(owner, LogLevel.WARNING, reason) },
        dependenciesStaged = { declarations ->
            runCatchingNonFatal { dependencyReporter.staged(owner, declarations) }
                .onFailure { logging.log(owner, LogLevel.ERROR, "Could not report the staged dependencies", it) }
        },
        failed = { declarations, error ->
            val level = if (declarations.any(FileDownload::required)) LogLevel.ERROR else LogLevel.WARNING
            runCatchingNonFatal { dependencyReporter.failure(owner, error, level) }
                .onFailure { logging.log(owner, LogLevel.ERROR, "Could not report the failed dependency download", it) }
        },
        remove = registrations::remove,
        automaticAllowed = ::automatic,
    ).also { registration ->
        registrations += registration
        registration.startAutomatic()
    }

    private fun installedPlugins(owner: Any) = runCatchingNonFatal { inventory.installedPlugins() }
        .onFailure { logging.log(owner, LogLevel.WARNING, "Could not read installed plugins; dependencies are treated as missing: ${it.message}") }
        .getOrNull().orEmpty()

    fun registerDependencies(owner: Any, dependencies: List<PluginDependency>): DownloadRegistration? {
        val installed = installedPlugins(owner)
        readiness.whenServerReady(Runnable { confirmDownloadedDependencies(owner, installedPlugins(owner)) })
        val lacking = dependencyRequestFactory.missing(dependencies, installed)
        val download = dependencyRequestFactory.create(dependencies, installed)
        val registration = download?.let { request ->
            register(owner, request.request) { declaration, path ->
                request.requirements[declaration.key]?.let { pluginJarVerifier.verify(path, it) }
            }
        }
        if (lacking.isNotEmpty()) announceMissing(owner, lacking, registration)
        return registration
    }

    private fun announceMissing(owner: Any, lacking: List<ExternalPluginDependency>, registration: DownloadRegistration?) {
        val items = lacking.map { MissingDependency(it.plugin, it.minimumVersion.toString(), it.downloadPage, it.artifact?.let { a -> listOf(a.uri) + a.mirrors }.orEmpty()) }
        val group = missing.add(ownerName(owner), items, registration)
        runCatchingNonFatal { dependencyReporter.missing(owner, group, automatic() && registration != null) }
            .onFailure { logging.log(owner, LogLevel.ERROR, "Could not report the missing dependencies", it) }
    }

    private fun ownerName(owner: Any): String = ownership.ownerDetails(owner)?.get("name") ?: PnLibraryIdentity.NAME

    /** Whether downloads that a plugin starts itself are allowed now. */
    fun automatic(): Boolean = configuration().automatic

    /** Saves the choice as the setting `downloads.automatic`. */
    fun setAutomatic(value: Boolean) = saveAutomatic(value)

    /** Dependencies still missing, with the code that installs each group. */
    internal fun missingGroups(): List<MissingGroup> = missing.pending()

    /**
     * Installs the group with this [code] now, whatever the automatic switch says: typing the code is the approval,
     * and a file that changed since the last download is accepted.
     */
    internal fun installGroup(code: String): CompletionStage<List<DownloadSnapshot>>? {
        val group = missing.find(code) ?: return null
        pins.forget(group.addresses)
        return group.install()
    }

    internal fun install(request: FileDownloads, declaration: FileDownload) {
        installBatch(request, listOf(declaration))
    }

    internal fun installBatch(
        request: FileDownloads,
        declarations: List<FileDownload>,
        beforePublish: () -> Unit = {},
        verifier: ((FileDownload, Path) -> Unit)? = null,
    ) {
        check(!closed.get()) { "Download manager is closed" }
        val prepared = mutableListOf<PreparedDownload>()
        try {
            declarations.forEach { prepared += artifactPreparer.prepare(request, it, verifier) }
        } catch (error: Throwable) {
            prepared.forEach { Files.deleteIfExists(it.staging) }
            throw error
        }
        if (prepared.isEmpty()) return
        try {
            beforePublish()
            check(!closed.get()) { "Download manager is closed" }
            filePublisher.publish(prepared)
        } catch (error: Throwable) {
            prepared.forEach { Files.deleteIfExists(it.staging) }
            throw error
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        registrations.toList().forEach { it.close() }
        registrations.clear()
        executor.shutdownNow()
    }

    private fun confirmDownloadedDependencies(owner: Any, installed: Map<String, String>) {
        val confirmed = dependencyStore.consumeInstalled(installed)
        if (confirmed.isEmpty()) return
        dependencyReporter.connected(owner, confirmed)
    }

    private fun List<FileDownload>.dependencyNames(): List<String> =
        mapNotNull { file ->
            file.key.takeIf { it.startsWith(DEPENDENCY_PREFIX) }?.removePrefix(DEPENDENCY_PREFIX)
        }

    private companion object {
        const val DEPENDENCY_PREFIX = "dependency:"
        const val MAX_BYTES = 512L * 1024L * 1024L
    }
}
