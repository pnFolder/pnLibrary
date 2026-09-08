package ru.privatenull.pnlibrary.core.updates

import ru.privatenull.pnlibrary.api.logging.LogLevel
import ru.privatenull.pnlibrary.spi.platform.PlatformAdapter
import ru.privatenull.pnlibrary.api.updates.PluginUpdateArtifact
import ru.privatenull.pnlibrary.api.updates.PluginUpdateRequest
import ru.privatenull.pnlibrary.api.updates.UpdateRegistration
import ru.privatenull.pnlibrary.api.updates.UpdateService
import ru.privatenull.pnlibrary.api.updates.UpdateSnapshot
import ru.privatenull.pnlibrary.api.updates.UpdateState
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.atomic.AtomicBoolean

internal class UpdateServiceImpl(private val platform: PlatformAdapter) : UpdateService, AutoCloseable {
    private val entries = CopyOnWriteArrayList<Registration>()

    override fun register(owner: Any, request: PluginUpdateRequest): UpdateRegistration {
        val info = platform.ownerDetails(owner)
        val product = info["name"] ?: request.repositoryName
        val version = info["version"] ?: error("Не удалось определить версию подключённого плагина")
        val jar = Paths.get(owner.javaClass.protectionDomain.codeSource.location.toURI()).toAbsolutePath().normalize()
        require(Files.isRegularFile(jar)) { "Плагин должен быть запущен из JAR" }
        val javaFeature = Runtime.version().feature()
        val artifact = request.artifactFor(javaFeature)
            ?: error("Для Java $javaFeature не зарегистрирован совместимый артефакт ${request.repositoryName}")
        val entry = Registration(owner, product, version, request, artifact, jar, jar.parent.resolve("update"))
        entries += entry
        entry.start()
        return entry
    }

    override fun registrations(): List<UpdateRegistration> = entries.toList()

    override fun find(product: String): UpdateRegistration? = entries.firstOrNull {
        it.product.equals(product, true) || it.request.repositoryName.equals(product, true)
    }

    override fun close() {
        entries.toList().forEach { it.close() }
        entries.clear()
    }

    private inner class Registration(
        private val owner: Any,
        val product: String,
        private val version: String,
        val request: PluginUpdateRequest,
        private val artifact: PluginUpdateArtifact,
        private val jar: java.nio.file.Path,
        private val updateDir: java.nio.file.Path,
    ) : UpdateRegistration {
        private val state = AtomicReference(UpdateSnapshot(
            product, version, null, request.channel, UpdateState.CHECKING,
            Runtime.version().feature(), artifact.minimumJava, request.automaticDownload, null, null,
        ))
        private var thread: Thread? = null
        private val closed = AtomicBoolean(false)
        private val actionThreads = java.util.concurrent.CopyOnWriteArraySet<Thread>()
        override val repository = "${request.repositoryOwner}/${request.repositoryName}"
        override val snapshot: UpdateSnapshot get() = state.get()

        fun start() {
            thread = MandatoryUpdateService.startProduct(
                owner, platform, version, request.repositoryOwner, request.repositoryName,
                request.channel.name.lowercase(), artifact.pattern, jar, updateDir,
                request.automaticDownload, artifact.minimumJava, ::updateState,
            )
        }

        override fun checkNow() = runOnce(download = false)
        override fun downloadNow() = runOnce(download = true)

        private fun runOnce(download: Boolean) {
            check(!closed.get()) { "Update registration is closed" }
            val action = Thread({
                runCatching {
                    MandatoryUpdateService.checkOnce(
                        owner, platform, version, request.repositoryOwner, request.repositoryName,
                        request.channel.name.lowercase(), artifact.pattern, jar, updateDir,
                        download, artifact.minimumJava, ::updateState,
                    )
                }.onFailure {
                    if (!closed.get()) state.set(UpdateSnapshot(product, version, snapshot.latestVersion, request.channel,
                        UpdateState.FAILED, Runtime.version().feature(), artifact.minimumJava,
                        request.automaticDownload, snapshot.releaseUrl, it.message))
                    if (!closed.get()) platform.log(owner, LogLevel.WARNING, "[$product] Не удалось выполнить обновление: ${it.message}")
                }.also {
                    actionThreads.remove(Thread.currentThread())
                }
            }, "pnLibrary-update-action-${request.repositoryName}").apply { isDaemon = true }
            actionThreads += action
            action.start()
        }

        private fun updateState(value: UpdateSnapshot) {
            if (!closed.get()) state.set(value)
        }

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            thread?.interrupt()
            actionThreads.forEach { it.interrupt() }
            actionThreads.clear()
            entries.remove(this)
        }
    }
}
