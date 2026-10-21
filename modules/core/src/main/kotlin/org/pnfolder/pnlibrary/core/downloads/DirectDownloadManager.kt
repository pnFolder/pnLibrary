package org.pnfolder.pnlibrary.core.downloads

import org.pnfolder.pnlibrary.api.downloads.DownloadRegistration
import org.pnfolder.pnlibrary.api.downloads.FileDownload
import org.pnfolder.pnlibrary.api.downloads.FileDownloads
import org.pnfolder.pnlibrary.api.logging.LogLevel
import org.pnfolder.pnlibrary.api.dependencies.PluginDependency
import org.pnfolder.pnlibrary.core.updates.engine.TrustedHttpClient
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnerMetadata
import org.pnfolder.pnlibrary.spi.platform.PlatformPluginInventory
import org.pnfolder.pnlibrary.spi.platform.PlatformReadiness
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/** Downloads, verifies and atomically publishes files declared by plugins, including missing plugin dependencies. */
internal class DirectDownloadManager(
    private val identity: PlatformIdentity,
    private val ownership: PlatformOwnerMetadata,
    private val inventory: PlatformPluginInventory,
    private val readiness: PlatformReadiness,
    private val logging: PlatformLogging,
    private val libraryData: Path,
    private val configuration: DownloadConfiguration,
    private val http: TrustedHttpClient = TrustedHttpClient(
        Duration.ofSeconds(8), Duration.ofSeconds(30),
    ),
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { action ->
        Thread(action, "pnLibrary-direct-downloads").apply { isDaemon = true }
    }
    private val registrations = CopyOnWriteArrayList<DownloadRegistrationImpl>()
    private val pluginJarVerifier = PluginJarVerifier(identity)
    private val filePublisher = AtomicDownloadPublisher(libraryData)
    private val artifactPreparer = DownloadArtifactPreparer(
        identity.type, libraryData, configuration, http, MAX_BYTES,
    )
    private val dependencyStore = DownloadedDependencyStore(libraryData)
    private val dependencyReporter = DependencyDownloadReporter(logging, ownership)
    private val dependencyRequestFactory = DependencyDownloadRequestFactory(
        requireNotNull(libraryData.parent) { "Library data directory must have a parent plugins directory" },
    )

    fun register(owner: Any, request: FileDownloads): DownloadRegistration = register(owner, request, null)

    private fun register(
        owner: Any,
        request: FileDownloads,
        verifier: ((FileDownload, Path) -> Unit)?,
    ): DownloadRegistration = DownloadRegistrationImpl(
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
            runCatching { dependencyReporter.staged(owner, declarations) }
        },
        failed = { declarations, error ->
            val level = if (declarations.any(FileDownload::required)) LogLevel.ERROR else LogLevel.WARNING
            runCatching { dependencyReporter.failure(owner, error, level) }
        },
        remove = registrations::remove,
    ).also { registration ->
        registrations += registration
        registration.startAutomatic()
    }

    fun registerDependencies(owner: Any, dependencies: List<PluginDependency>): DownloadRegistration? {
        val installed = runCatching { inventory.installedPlugins() }.getOrNull().orEmpty()
        readiness.whenServerReady(Runnable {
            confirmDownloadedDependencies(owner, runCatching { inventory.installedPlugins() }.getOrNull().orEmpty())
        })
        val download = dependencyRequestFactory.create(dependencies, installed) ?: return null
        return register(owner, download.request) { declaration, path ->
            download.requirements[declaration.key]?.let { pluginJarVerifier.verify(path, it) }
        }
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
