package ru.privatenull.pnlibrary.core.downloads

import ru.privatenull.pnlibrary.api.downloads.FileDownload
import ru.privatenull.pnlibrary.api.downloads.FileDownloads
import ru.privatenull.pnlibrary.api.downloads.DownloadRegistration
import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.plugin.PluginDependency
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.update.TrustedHttpClient
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

internal class DirectDownloadManager(
    private val platform: PlatformAdapter,
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
    private val pluginJarVerifier = PluginJarVerifier(platform.type)
    private val filePublisher = AtomicDownloadPublisher(libraryData)
    private val artifactPreparer = DownloadArtifactPreparer(
        platform.type, libraryData, configuration, http, MAX_BYTES,
    )
    private val dependencyStore = DownloadedDependencyStore(libraryData)
    private val dependencyReporter = DependencyDownloadReporter(platform)
    private val dependencyRequestFactory = DependencyDownloadRequestFactory(
        requireNotNull(libraryData.parent) { "не найдена папка плагинов" },
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
        blocked = { reason -> platform.log(owner, LogLevel.WARNING, reason) },
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
        val installed = runCatching { platform.installedPlugins() }.getOrNull().orEmpty()
        platform.whenServerReady(Runnable {
            confirmDownloadedDependencies(owner, runCatching { platform.installedPlugins() }.getOrNull().orEmpty())
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
        check(!closed.get()) { "система загрузок закрыта" }
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
            check(!closed.get()) { "система загрузок закрыта" }
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
