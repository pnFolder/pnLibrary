package ru.privatenull.pnlibrary.core.downloads

import ru.privatenull.pnlibrary.api.downloads.DownloadDeclaration
import ru.privatenull.pnlibrary.api.downloads.DownloadDestination
import ru.privatenull.pnlibrary.api.downloads.PluginDownloads
import ru.privatenull.pnlibrary.api.downloads.DownloadRegistration
import ru.privatenull.pnlibrary.api.downloads.DownloadSnapshot
import ru.privatenull.pnlibrary.api.downloads.DownloadState
import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.update.EmbeddedDescriptorReader
import ru.privatenull.pnlibrary.api.version.PnLibraryApi
import ru.privatenull.pnlibrary.update.TrustedHttpClient
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.Paths
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicReference

internal class DirectDownloadManager(
    private val platform: PlatformAdapter,
    private val libraryData: Path,
    private val configuration: DownloadConfiguration,
    private val http: TrustedHttpClient = TrustedHttpClient(
        Duration.ofSeconds(8), Duration.ofSeconds(30), configuration.allowedHosts,
    ),
) : AutoCloseable {
    private val closed = AtomicBoolean(false)
    private val executor: ExecutorService = Executors.newSingleThreadExecutor { action ->
        Thread(action, "pnLibrary-direct-downloads").apply { isDaemon = true }
    }

    fun register(owner: Any, request: PluginDownloads): DownloadRegistration = Registration(owner, request).also { registration ->
        val automatic = request.declarations.filter { it.automatic && !alreadyInstalled(it) }
        if (automatic.isNotEmpty()) registration.start(automatic, automaticPolicy = true)
    }

    internal fun install(request: PluginDownloads, declaration: DownloadDeclaration) {
        installBatch(request, listOf(declaration))
    }

    internal fun installBatch(
        request: PluginDownloads,
        declarations: List<DownloadDeclaration>,
        beforePublish: () -> Unit = {},
    ) {
        check(!closed.get()) { "система загрузок закрыта" }
        val prepared = mutableListOf<Prepared>()
        try {
            declarations.filterNot(::alreadyInstalled).forEach { prepared += prepare(request, it) }
        } catch (error: Throwable) {
            prepared.forEach { Files.deleteIfExists(it.staging) }
            throw error
        }
        if (prepared.isEmpty()) return
        try {
            beforePublish()
            check(!closed.get()) { "система загрузок закрыта" }
            publishAtomically(prepared)
        } catch (error: Throwable) {
            prepared.forEach { Files.deleteIfExists(it.staging) }
            throw error
        }
    }

    private fun prepare(request: PluginDownloads, declaration: DownloadDeclaration): Prepared {
        require(declaration.source.supports(platform.type, Runtime.version().feature())) {
            "файл не поддерживает ${platform.type.displayName} / Java ${Runtime.version().feature()}"
        }
        val maximum = declaration.source.size?.coerceAtMost(MAX_BYTES)?.toInt() ?: MAX_BYTES.toInt()
        val bytes = http.get(declaration.source.uri, maximum)
        declaration.source.size?.let { require(bytes.size.toLong() == it) { "размер файла не совпадает" } }
        declaration.source.sha256?.let { require(sha256(bytes).equals(it, true)) { "SHA-256 файла не совпадает" } }

        val target = target(request, declaration)
        val staging = libraryData.resolve("downloads/staging").resolve("${UUID.randomUUID()}-${target.fileName}")
        Files.createDirectories(staging.parent)
        Files.write(staging, bytes)
        if (declaration is DownloadDeclaration.Component) verifyComponent(staging, declaration)
        return Prepared(staging, target)
    }

    private fun publishAtomically(prepared: List<Prepared>) {
        val transaction = libraryData.resolve("downloads/transactions/${UUID.randomUUID()}")
        val backups = mutableListOf<Pair<Path, Path>>()
        val published = mutableListOf<Path>()
        try {
            prepared.forEachIndexed { index, item ->
                Files.createDirectories(item.target.parent)
                if (Files.exists(item.target)) {
                    val backup = transaction.resolve("$index-${item.target.fileName}")
                    Files.createDirectories(backup.parent)
                    Files.move(item.target, backup, StandardCopyOption.REPLACE_EXISTING)
                    backups.add(item.target to backup)
                }
                move(item.staging, item.target)
                published.add(item.target)
            }
        } catch (error: Throwable) {
            published.asReversed().forEach(Files::deleteIfExists)
            backups.asReversed().forEach { (target, backup) -> if (Files.exists(backup)) move(backup, target) }
            throw error
        } finally {
            prepared.forEach { Files.deleteIfExists(it.staging) }
            backups.forEach { (_, backup) -> Files.deleteIfExists(backup) }
            runCatching { Files.deleteIfExists(transaction) }
        }
    }

    private fun move(source: Path, target: Path) {
        try { Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
        catch (_: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun alreadyInstalled(declaration: DownloadDeclaration): Boolean {
        val installed = runCatching { platform.installedPlugins() }.getOrNull().orEmpty()
        val expected = when (declaration) {
            is DownloadDeclaration.Component -> declaration.component.value
            is DownloadDeclaration.Plugin -> declaration.plugin
            is DownloadDeclaration.File -> return false
        }
        val version = installed.entries.firstOrNull { it.key.equals(expected, true) }?.value ?: return false
        val minimum = when (declaration) {
            is DownloadDeclaration.Component -> declaration.version
            is DownloadDeclaration.Plugin -> declaration.minimumVersion
            else -> return false
        }
        return ru.privatenull.pnlibrary.api.version.SemanticVersion.tryParse(version)?.let { it >= minimum } == true
    }

    private fun verifyComponent(path: Path, expected: DownloadDeclaration.Component) {
        require(expected.supportedApi.supports(PnLibraryApi.VERSION)) {
            "компонент не поддерживает pnLibrary API ${PnLibraryApi.VERSION}"
        }
        val descriptor = runCatching { EmbeddedDescriptorReader().read(path) }.getOrElse { error ->
            require(expected.source.size != null && expected.source.sha256 != null) {
                "JAR без component.json требует точные size и SHA-256: ${error.message}"
            }
            return
        }
        require(descriptor.id == expected.component) { "ID компонента внутри JAR не совпадает" }
        require(descriptor.version == expected.version) { "версия компонента внутри JAR не совпадает" }
        require(descriptor.supportedApi == expected.supportedApi) { "диапазон API внутри JAR не совпадает" }
    }

    private fun target(request: PluginDownloads, declaration: DownloadDeclaration): Path {
        val remoteName = Paths.get(declaration.source.uri.path).fileName?.toString()
            ?.takeIf { it.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]*")) }
            ?: "${declaration.key}.bin"
        val root: Path
        val relative: String
        when (declaration) {
            is DownloadDeclaration.Component, is DownloadDeclaration.Plugin -> {
                root = requireNotNull(libraryData.parent) { "не найдена папка плагинов" }.resolve("update")
                relative = remoteName
            }
            is DownloadDeclaration.File -> {
                root = when (declaration.destination) {
                    DownloadDestination.PLUGINS -> requireNotNull(libraryData.parent).resolve("update")
                    DownloadDestination.DATA_FOLDER -> requireNotNull(request.dataDirectory) {
                        "для DATA_FOLDER передайте папку в downloads(dataDirectory, ...)"
                    }
                    DownloadDestination.CACHE -> libraryData.resolve("downloads/cache")
                }
                relative = declaration.relativePath
            }
        }
        val normalizedRoot = root.toAbsolutePath().normalize()
        val destination = when (declaration) {
            is DownloadDeclaration.Component, is DownloadDeclaration.Plugin -> DownloadDestination.PLUGINS
            is DownloadDeclaration.File -> declaration.destination
        }
        require(destination in configuration.destinations) { "назначение $destination запрещено конфигурацией" }
        return normalizedRoot.resolve(relative).normalize().also {
            require(it.startsWith(normalizedRoot)) { "путь загрузки выходит за разрешённую папку" }
        }
    }

    override fun close() { if (closed.compareAndSet(false, true)) executor.shutdownNow() }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private data class Prepared(val staging: Path, val target: Path)

    private inner class Registration(private val owner: Any, private val request: PluginDownloads) : DownloadRegistration {
        private val registrationClosed = AtomicBoolean(false)
        private val activeDownload = AtomicReference<CompletableFuture<List<DownloadSnapshot>>?>()
        private val state = AtomicReference(request.declarations.map { declaration ->
            DownloadSnapshot(declaration.key, if (alreadyInstalled(declaration)) DownloadState.CURRENT else DownloadState.DECLARED)
        })

        override fun snapshots(): List<DownloadSnapshot> = state.get().toList()

        override fun downloadNow(): CompletionStage<List<DownloadSnapshot>> =
            start(request.declarations.filterNot(::alreadyInstalled), automaticPolicy = false)

        fun start(declarations: List<DownloadDeclaration>, automaticPolicy: Boolean): CompletableFuture<List<DownloadSnapshot>> {
            val promise = CompletableFuture<List<DownloadSnapshot>>()
            if (registrationClosed.get() || closed.get()) {
                promise.completeExceptionally(IllegalStateException("регистрация загрузок закрыта")); return promise
            }
            if (!configuration.enabled || (automaticPolicy && !configuration.automatic)) {
                val reason = if (!configuration.enabled) "система загрузок отключена" else "автозагрузка запрещена в downloads.yml"
                state.set(request.declarations.map { DownloadSnapshot(it.key, DownloadState.BLOCKED, reason) })
                platform.log(owner, LogLevel.WARNING, "[pnLibrary] $reason")
                promise.complete(snapshots()); return promise
            }
            if (declarations.isEmpty()) { promise.complete(snapshots()); return promise }
            activeDownload.get()?.let { return it }
            if (!activeDownload.compareAndSet(null, promise)) return activeDownload.get() ?: promise
            state.set(request.declarations.map {
                DownloadSnapshot(it.key, if (it in declarations) DownloadState.DOWNLOADING else DownloadState.CURRENT)
            })
            try {
                executor.execute {
                if (registrationClosed.get() || closed.get()) {
                    promise.complete(snapshots())
                    activeDownload.compareAndSet(promise, null)
                    return@execute
                }
                runCatching {
                    installBatch(request, declarations) {
                        check(!registrationClosed.get()) { "регистрация загрузок закрыта" }
                    }
                }
                    .onSuccess {
                        if (!registrationClosed.get() && !closed.get()) state.set(request.declarations.map {
                            DownloadSnapshot(it.key, when {
                                it in declarations -> DownloadState.STAGED
                                alreadyInstalled(it) -> DownloadState.CURRENT
                                else -> DownloadState.DECLARED
                            })
                        })
                        promise.complete(snapshots())
                    }
                    .onFailure { error ->
                        if (!registrationClosed.get() && !closed.get()) {
                            state.set(request.declarations.map { DownloadSnapshot(it.key, DownloadState.FAILED, error.message) })
                            val level = if (declarations.any { it.required }) LogLevel.ERROR else LogLevel.WARNING
                            platform.log(owner, level, "[pnLibrary] Пакет загрузок не подготовлен: ${error.message}", error)
                        }
                        promise.complete(snapshots())
                    }
                    .also { activeDownload.compareAndSet(promise, null) }
                }
            } catch (error: Throwable) {
                activeDownload.compareAndSet(promise, null)
                promise.completeExceptionally(error)
            }
            return promise
        }

        override fun close() {
            if (registrationClosed.compareAndSet(false, true)) {
                state.set(request.declarations.map { DownloadSnapshot(it.key, DownloadState.CLOSED) })
                activeDownload.getAndSet(null)?.completeExceptionally(IllegalStateException("регистрация загрузок закрыта"))
            }
        }
    }

    private companion object { const val MAX_BYTES = 512L * 1024L * 1024L }
}
