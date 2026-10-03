package ru.privatenull.pnlibrary.core.downloads

import ru.privatenull.pnlibrary.api.downloads.DownloadDestination
import ru.privatenull.pnlibrary.api.downloads.FileDownload
import ru.privatenull.pnlibrary.api.downloads.FileDownloads
import ru.privatenull.pnlibrary.api.downloads.DownloadRegistration
import ru.privatenull.pnlibrary.api.downloads.DownloadSnapshot
import ru.privatenull.pnlibrary.api.downloads.DownloadState
import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.api.plugin.PluginDependency
import ru.privatenull.pnlibrary.api.version.SemanticVersion
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.update.TrustedHttpClient
import ru.privatenull.pnlibrary.console.ConsoleCard
import ru.privatenull.pnlibrary.console.ConsoleTheme
import ru.privatenull.pnlibrary.console.ConsoleTree
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicReference

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
    private val registrations = CopyOnWriteArrayList<Registration>()
    private val pluginJarVerifier = PluginJarVerifier(platform.type)
    private val filePublisher = AtomicDownloadPublisher(libraryData)
    private val artifactPreparer = DownloadArtifactPreparer(
        platform.type, libraryData, configuration, http, MAX_BYTES,
    )
    private val dependencyStore = DownloadedDependencyStore(libraryData)

    fun register(owner: Any, request: FileDownloads): DownloadRegistration = register(owner, request, null)

    private fun register(
        owner: Any,
        request: FileDownloads,
        verifier: ((FileDownload, Path) -> Unit)?,
    ): DownloadRegistration = Registration(owner, request, verifier).also { registration ->
        registrations += registration
        val automatic = request.files.filter { it.automatic }
        if (automatic.isNotEmpty()) registration.start(automatic, automaticPolicy = true)
    }

    fun registerDependencies(owner: Any, dependencies: List<PluginDependency>): DownloadRegistration? {
        val installed = runCatching { platform.installedPlugins() }.getOrNull().orEmpty()
        platform.whenServerReady(Runnable {
            confirmDownloadedDependencies(owner, runCatching { platform.installedPlugins() }.getOrNull().orEmpty())
        })
        val downloadable = dependencies.mapNotNull { dependency ->
            val external = dependency.external ?: return@mapNotNull null
            val artifact = external.artifact ?: return@mapNotNull null
            if (!dependency.automaticDownload) return@mapNotNull null
            val installedVersion = installed.entries.firstOrNull { it.key.equals(external.plugin, true) }?.value
            if (installedVersion != null && SemanticVersion.tryParse(installedVersion)?.let(external.versions::accepts) == true) {
                return@mapNotNull null
            }
            Triple(dependency, external, artifact)
        }
        if (downloadable.isEmpty()) return null
        val pluginDirectory = requireNotNull(libraryData.parent) { "не найдена папка плагинов" }
        val request = FileDownloads.builder().dataDirectory(pluginDirectory.resolve("update")).also { builder ->
            downloadable.forEach { (dependency, external, artifact) ->
                val safePluginName = external.plugin.replace(Regex("[^A-Za-z0-9._-]+"), "-")
                    .trim('-', '.')
                    .ifBlank { "plugin" }
                val fileName = "$safePluginName.jar"
                builder.file("dependency:${external.plugin}") { file ->
                    file.url(artifact.uri.toString())
                        .required(dependency.required)
                        .automaticDownload(true)
                        .forceAutomaticDownload(dependency.forceAutomaticDownload)
                        .destination(DownloadDestination.DATA_FOLDER, fileName)
                    val expectedSize = artifact.size
                    val expectedHash = artifact.sha256
                    if (expectedSize != null && expectedHash != null) {
                        file.integrity(expectedSize, expectedHash)
                    }
                }
            }
        }.build()
        val requirements = downloadable.associate { (_, external, _) -> "dependency:${external.plugin}" to external }
        return register(owner, request) { declaration, path ->
            requirements[declaration.key]?.let { pluginJarVerifier.verify(path, it) }
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

    private inner class Registration(
        private val owner: Any,
        private val request: FileDownloads,
        private val verifier: ((FileDownload, Path) -> Unit)?,
    ) : DownloadRegistration {
        private val registrationClosed = AtomicBoolean(false)
        private val activeDownload = AtomicReference<CompletableFuture<List<DownloadSnapshot>>?>()
        private val state = AtomicReference(request.files.map { DownloadSnapshot(it.key, DownloadState.DECLARED) })

        override val isClosed: Boolean get() = registrationClosed.get()

        override fun snapshots(): List<DownloadSnapshot> =
            java.util.Collections.unmodifiableList(ArrayList(state.get()))

        override fun downloadNow(): CompletionStage<List<DownloadSnapshot>> =
            start(request.files, automaticPolicy = false)

        fun start(declarations: List<FileDownload>, automaticPolicy: Boolean): CompletableFuture<List<DownloadSnapshot>> {
            val promise = CompletableFuture<List<DownloadSnapshot>>()
            if (registrationClosed.get() || closed.get()) {
                promise.completeExceptionally(IllegalStateException("регистрация загрузок закрыта"))
                return promise
            }

            val effectiveDeclarations = if (automaticPolicy && !configuration.automatic) {
                declarations.filter(FileDownload::forceAutomaticDownload)
            } else {
                declarations
            }

            if (!configuration.enabled) {
                return block(promise, "система загрузок отключена")
            }
            if (effectiveDeclarations.isEmpty()) {
                if (automaticPolicy && declarations.isNotEmpty()) {
                    return block(promise, "автозагрузка запрещена в downloads.yml")
                }
                promise.complete(snapshots())
                return promise
            }

            activeDownload.get()?.let { return it }
            if (!activeDownload.compareAndSet(null, promise)) return activeDownload.get() ?: promise

            updateState(effectiveDeclarations, DownloadState.DOWNLOADING)
            try {
                executor.execute {
                    executeDownload(effectiveDeclarations, promise)
                }
            } catch (error: Throwable) {
                activeDownload.compareAndSet(promise, null)
                promise.completeExceptionally(error)
            }
            return promise
        }

        private fun block(
            promise: CompletableFuture<List<DownloadSnapshot>>,
            reason: String,
        ): CompletableFuture<List<DownloadSnapshot>> {
            state.set(request.files.map { DownloadSnapshot(it.key, DownloadState.BLOCKED, reason) })
            platform.log(owner, LogLevel.WARNING, reason)
            promise.complete(snapshots())
            return promise
        }

        private fun executeDownload(
            declarations: List<FileDownload>,
            promise: CompletableFuture<List<DownloadSnapshot>>,
        ) {
            if (registrationClosed.get() || closed.get()) {
                promise.complete(snapshots())
                activeDownload.compareAndSet(promise, null)
                return
            }

            runCatching {
                installBatch(
                    request = request,
                    declarations = declarations,
                    beforePublish = {
                        check(!registrationClosed.get()) { "регистрация загрузок закрыта" }
                    },
                    verifier = verifier,
                )
                if (declarations.containsDependencies()) {
                    dependencyStore.record(declarations.dependencyNames())
                }
            }.onSuccess {
                completeSuccessfully(declarations, promise)
            }.onFailure { error ->
                completeWithFailure(declarations, promise, error)
            }

            activeDownload.compareAndSet(promise, null)
        }

        private fun completeSuccessfully(
            declarations: List<FileDownload>,
            promise: CompletableFuture<List<DownloadSnapshot>>,
        ) {
            if (!registrationClosed.get() && !closed.get()) {
                updateState(declarations, DownloadState.STAGED)
            }
            promise.complete(snapshots())
            if (declarations.containsDependencies()) {
                runCatching { showDependencySuccess(owner, declarations) }
            }
        }

        private fun completeWithFailure(
            declarations: List<FileDownload>,
            promise: CompletableFuture<List<DownloadSnapshot>>,
            error: Throwable,
        ) {
            if (!registrationClosed.get() && !closed.get()) {
                state.set(request.files.map {
                    DownloadSnapshot(it.key, DownloadState.FAILED, error.message)
                })
                val level = if (declarations.any(FileDownload::required)) {
                    LogLevel.ERROR
                } else {
                    LogLevel.WARNING
                }
                runCatching { showFailure(owner, error, level) }
            }
            promise.complete(snapshots())
        }

        private fun updateState(
            activeDeclarations: List<FileDownload>,
            activeState: DownloadState,
        ) {
            state.set(request.files.map { declaration ->
                val newState = if (declaration in activeDeclarations) activeState else DownloadState.DECLARED
                DownloadSnapshot(declaration.key, newState)
            })
        }

        private fun List<FileDownload>.containsDependencies(): Boolean =
            any { it.key.startsWith("dependency:") }

        private fun List<FileDownload>.dependencyNames(): List<String> =
            mapNotNull { it.key.takeIf { key -> key.startsWith("dependency:") }?.removePrefix("dependency:") }

        private fun showFailure(
            owner: Any,
            error: Throwable,
            level: LogLevel,
        ) {
            val details = DownloadFailureInterpreter.explain(error)
            val ownerName = platform.ownerDetails(owner)["name"] ?: "pnLibrary"
            val theme = ConsoleTheme("§6", "§c", "§f", "§8", "§r")
            ConsoleCard.builder(theme, "ЗАВИСИМОСТЬ НЕ ПОДГОТОВЛЕНА")
                .mascot("x.x", ownerName, details.headline)
                .blank()
                .tree(ConsoleTree.builder("Причина")
                    .child(details.reason)
                    .build())
                .blank()
                .tree(ConsoleTree.builder("Что может сделать пользователь")
                    .child(details.userAction)
                    .build())
                .blank()
                .tree(ConsoleTree.builder("Что сообщить разработчику")
                    .child(details.developerAction)
                    .build())
                .blank()
                .status("Файл не установлен")
                .build().send { line -> platform.console(owner, line) }

            if (error !is IllegalArgumentException && error !is java.io.IOException) {
                platform.log(owner, level, "Пакет загрузок не подготовлен: ${details.reason}", error)
            }
        }

        private fun showDependencySuccess(owner: Any, declarations: List<FileDownload>) {
            val ownerName = platform.ownerDetails(owner)["name"] ?: "pnLibrary"
            val theme = ConsoleTheme("§6", "§a", "§f", "§8", "§r")
            val files = declarations.map { Paths.get(it.relativePath).fileName.toString() }
            val tree = ConsoleTree.builder("Скачанные зависимости")
                .apply { files.forEach { child(it) } }
                .build()
            ConsoleCard.builder(theme, "ЗАВИСИМОСТИ ПОДГОТОВЛЕНЫ")
                .mascot("^.^", ownerName, "файлы готовы к запуску")
                .blank()
                .tree(tree)
                .blank()
                .tree(ConsoleTree.builder("Что дальше")
                    .child("Перезапусти сервер")
                    .child("Плагины загрузятся при следующем запуске")
                    .build())
                .blank()
                .status("Загрузка завершена")
                .build().send { line -> platform.console(owner, line) }
        }

        override fun close() {
            if (registrationClosed.compareAndSet(false, true)) {
                state.set(request.files.map { DownloadSnapshot(it.key, DownloadState.CLOSED) })
                activeDownload.getAndSet(null)?.completeExceptionally(IllegalStateException("регистрация загрузок закрыта"))
                registrations.remove(this)
            }
        }
    }

    private fun confirmDownloadedDependencies(owner: Any, installed: Map<String, String>) {
        val confirmed = dependencyStore.consumeInstalled(installed)
        if (confirmed.isEmpty()) return

        val theme = ConsoleTheme("§6", "§a", "§f", "§8", "§r")
        val tree = ConsoleTree.builder("Подключённые зависимости")
            .apply {
                confirmed.forEach { child("${it.name} ${it.version}") }
            }
            .build()
        val ownerName = platform.ownerDetails(owner)["name"] ?: "pnLibrary"
        ConsoleCard.builder(theme, "ЗАВИСИМОСТИ ПОДКЛЮЧЕНЫ")
            .mascot("^.^", ownerName, "сервер успешно перезапущен")
            .blank()
            .tree(tree)
            .blank()
            .status("Зависимости загружены и работают")
            .build().send { line -> platform.console(owner, line) }

    }

    private companion object { const val MAX_BYTES = 512L * 1024L * 1024L }
}
