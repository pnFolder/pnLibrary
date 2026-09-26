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
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicReference
import java.util.jar.JarFile
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

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
    private val registrations = CopyOnWriteArrayList<Registration>()

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
        val updateDirectory = requireNotNull(libraryData.parent) { "не найдена папка плагинов" }.resolve("update")
        val request = FileDownloads.builder().dataDirectory(updateDirectory).also { builder ->
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
            requirements[declaration.key]?.let { verifyPluginJar(path, it) }
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
        val prepared = mutableListOf<Prepared>()
        try {
            declarations.forEach { prepared += prepare(request, it, verifier) }
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

    private fun prepare(
        request: FileDownloads,
        declaration: FileDownload,
        verifier: ((FileDownload, Path) -> Unit)?,
    ): Prepared {
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
        verifier?.invoke(declaration, staging)
        return Prepared(staging, target)
    }

    private fun verifyPluginJar(path: Path, expected: ru.privatenull.pnlibrary.api.updates.ExternalPluginDependency) {
        val metadata = JarFile(path.toFile()).use { jar ->
            val descriptorName = when (platform.type) {
                ru.privatenull.pnlibrary.api.platform.PlatformType.BUKKIT -> "plugin.yml"
                ru.privatenull.pnlibrary.api.platform.PlatformType.BUNGEECORD -> "bungee.yml"
                ru.privatenull.pnlibrary.api.platform.PlatformType.VELOCITY -> "velocity-plugin.json"
            }
            val entry = jar.getJarEntry(descriptorName)?.let { descriptorName to it }
                ?: throw IllegalArgumentException("JAR плагина не содержит поддерживаемого descriptor")
            @Suppress("UNCHECKED_CAST")
            val values = jar.getInputStream(entry.second).use { input ->
                Yaml(SafeConstructor(LoaderOptions())).load<Any?>(input) as? Map<String, Any?>
            } ?: throw IllegalArgumentException("descriptor плагина повреждён")
            val names = listOfNotNull(values["id"]?.toString(), values["name"]?.toString())
            val version = values["version"]?.toString()
                ?: throw IllegalArgumentException("descriptor плагина не содержит version")
            names to version
        }
        require(metadata.first.any { it.equals(expected.plugin, true) }) {
            "скачанный JAR объявляет ${metadata.first.joinToString("/")}, ожидался ${expected.plugin}"
        }
        val version = SemanticVersion.tryParse(metadata.second)
            ?: throw IllegalArgumentException("версия скачанного ${expected.plugin} не распознана: ${metadata.second}")
        require(expected.versions.accepts(version)) {
            "версия скачанного ${expected.plugin} $version не соответствует требованию ${expected.minimumVersion}"
        }
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

    private fun target(request: FileDownloads, declaration: FileDownload): Path {
        val root = when (declaration.destination) {
            DownloadDestination.DATA_FOLDER -> requireNotNull(request.dataDirectory) {
                "для DATA_FOLDER передайте папку в downloads(dataDirectory, ...)"
            }
            DownloadDestination.CACHE -> libraryData.resolve("downloads/cache")
        }
        val relative = declaration.relativePath
        val normalizedRoot = root.toAbsolutePath().normalize()
        require(declaration.destination in configuration.destinations) {
            "назначение ${declaration.destination} запрещено конфигурацией"
        }
        return normalizedRoot.resolve(relative).normalize().also {
            require(it.startsWith(normalizedRoot)) { "путь загрузки выходит за разрешённую папку" }
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        registrations.toList().forEach { it.close() }
        registrations.clear()
        executor.shutdownNow()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private data class Prepared(val staging: Path, val target: Path)

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
                promise.completeExceptionally(IllegalStateException("регистрация загрузок закрыта")); return promise
            }
            val effectiveDeclarations = if (automaticPolicy && !configuration.automatic) {
                declarations.filter(FileDownload::forceAutomaticDownload)
            } else declarations
            if (!configuration.enabled) {
                val reason = "система загрузок отключена"
                state.set(request.files.map { DownloadSnapshot(it.key, DownloadState.BLOCKED, reason) })
                platform.log(owner, LogLevel.WARNING, reason)
                promise.complete(snapshots()); return promise
            }
            if (effectiveDeclarations.isEmpty()) {
                if (automaticPolicy && declarations.isNotEmpty()) {
                    val reason = "автозагрузка запрещена в downloads.yml"
                    state.set(request.files.map { DownloadSnapshot(it.key, DownloadState.BLOCKED, reason) })
                    platform.log(owner, LogLevel.WARNING, reason)
                }
                promise.complete(snapshots()); return promise
            }
            activeDownload.get()?.let { return it }
            if (!activeDownload.compareAndSet(null, promise)) return activeDownload.get() ?: promise
            state.set(request.files.map {
                DownloadSnapshot(it.key, if (it in effectiveDeclarations) DownloadState.DOWNLOADING else DownloadState.DECLARED)
            })
            try {
                executor.execute {
                if (registrationClosed.get() || closed.get()) {
                    promise.complete(snapshots())
                    activeDownload.compareAndSet(promise, null)
                    return@execute
                }
                runCatching {
                    installBatch(request, effectiveDeclarations, {
                        check(!registrationClosed.get()) { "регистрация загрузок закрыта" }
                    }, verifier)
                }
                    .onSuccess {
                        if (!registrationClosed.get() && !closed.get()) state.set(request.files.map {
                            DownloadSnapshot(it.key, when {
                                it in effectiveDeclarations -> DownloadState.STAGED
                                else -> DownloadState.DECLARED
                            })
                        })
                        promise.complete(snapshots())
                    }
                    .onFailure { error ->
                        if (!registrationClosed.get() && !closed.get()) {
                            state.set(request.files.map { DownloadSnapshot(it.key, DownloadState.FAILED, error.message) })
                            val level = if (effectiveDeclarations.any { it.required }) LogLevel.ERROR else LogLevel.WARNING
                            platform.log(owner, level, "Пакет загрузок не подготовлен: ${error.message}", error)
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
                state.set(request.files.map { DownloadSnapshot(it.key, DownloadState.CLOSED) })
                activeDownload.getAndSet(null)?.completeExceptionally(IllegalStateException("регистрация загрузок закрыта"))
                registrations.remove(this)
            }
        }
    }

    private companion object { const val MAX_BYTES = 512L * 1024L * 1024L }
}
