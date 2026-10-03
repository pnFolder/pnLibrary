package ru.privatenull.pnlibrary.core.downloads

import ru.privatenull.pnlibrary.api.downloads.DownloadRegistration
import ru.privatenull.pnlibrary.api.downloads.DownloadSnapshot
import ru.privatenull.pnlibrary.api.downloads.DownloadState
import ru.privatenull.pnlibrary.api.downloads.FileDownload
import ru.privatenull.pnlibrary.api.downloads.FileDownloads
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal class DownloadRegistrationImpl(
    private val request: FileDownloads,
    private val configuration: DownloadConfiguration,
    private val executor: Executor,
    private val managerIsClosed: () -> Boolean,
    private val install: (List<FileDownload>, beforePublish: () -> Unit) -> Unit,
    private val blocked: (String) -> Unit,
    private val dependenciesStaged: (List<FileDownload>) -> Unit,
    private val failed: (List<FileDownload>, Throwable) -> Unit,
    private val remove: (DownloadRegistrationImpl) -> Unit,
) : DownloadRegistration {
    private val closed = AtomicBoolean(false)
    private val activeDownload = AtomicReference<CompletableFuture<List<DownloadSnapshot>>?>()
    private val currentSnapshots = AtomicReference(declaredSnapshots())

    override val isClosed: Boolean
        get() = closed.get()

    override fun snapshots(): List<DownloadSnapshot> =
        Collections.unmodifiableList(ArrayList(currentSnapshots.get()))

    override fun downloadNow(): CompletionStage<List<DownloadSnapshot>> =
        start(request.files, automaticPolicy = false)

    fun startAutomatic() {
        val automaticFiles = request.files.filter(FileDownload::automatic)
        if (automaticFiles.isNotEmpty()) start(automaticFiles, automaticPolicy = true)
    }

    private fun start(
        declarations: List<FileDownload>,
        automaticPolicy: Boolean,
    ): CompletableFuture<List<DownloadSnapshot>> {
        val promise = CompletableFuture<List<DownloadSnapshot>>()
        if (closed.get() || managerIsClosed()) {
            promise.completeExceptionally(IllegalStateException("регистрация загрузок закрыта"))
            return promise
        }

        val selectedFiles = selectFiles(declarations, automaticPolicy)
        if (!configuration.enabled) return blocked(promise, "система загрузок отключена")
        if (selectedFiles.isEmpty()) {
            if (automaticPolicy && declarations.isNotEmpty()) {
                return blocked(promise, "автозагрузка запрещена в downloads.yml")
            }
            promise.complete(snapshots())
            return promise
        }

        activeDownload.get()?.let { return it }
        if (!activeDownload.compareAndSet(null, promise)) return activeDownload.get() ?: promise

        updateSnapshots(selectedFiles, DownloadState.DOWNLOADING)
        try {
            executor.execute { execute(selectedFiles, promise) }
        } catch (error: Throwable) {
            activeDownload.compareAndSet(promise, null)
            promise.completeExceptionally(error)
        }
        return promise
    }

    private fun selectFiles(declarations: List<FileDownload>, automaticPolicy: Boolean): List<FileDownload> =
        if (automaticPolicy && !configuration.automatic) {
            declarations.filter(FileDownload::forceAutomaticDownload)
        } else {
            declarations
        }

    private fun blocked(
        promise: CompletableFuture<List<DownloadSnapshot>>,
        reason: String,
    ): CompletableFuture<List<DownloadSnapshot>> {
        currentSnapshots.set(request.files.map { DownloadSnapshot(it.key, DownloadState.BLOCKED, reason) })
        blocked(reason)
        promise.complete(snapshots())
        return promise
    }

    private fun execute(
        declarations: List<FileDownload>,
        promise: CompletableFuture<List<DownloadSnapshot>>,
    ) {
        if (closed.get() || managerIsClosed()) {
            promise.complete(snapshots())
            activeDownload.compareAndSet(promise, null)
            return
        }

        runCatching {
            install(declarations) {
                check(!closed.get()) { "регистрация загрузок закрыта" }
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
        if (!closed.get() && !managerIsClosed()) {
            updateSnapshots(declarations, DownloadState.STAGED)
        }
        promise.complete(snapshots())
        if (declarations.containsDependencies()) dependenciesStaged(declarations)
    }

    private fun completeWithFailure(
        declarations: List<FileDownload>,
        promise: CompletableFuture<List<DownloadSnapshot>>,
        error: Throwable,
    ) {
        if (!closed.get() && !managerIsClosed()) {
            currentSnapshots.set(request.files.map {
                DownloadSnapshot(it.key, DownloadState.FAILED, error.message)
            })
            failed(declarations, error)
        }
        promise.complete(snapshots())
    }

    private fun updateSnapshots(activeFiles: List<FileDownload>, state: DownloadState) {
        currentSnapshots.set(request.files.map { file ->
            DownloadSnapshot(file.key, if (file in activeFiles) state else DownloadState.DECLARED)
        })
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        currentSnapshots.set(request.files.map { DownloadSnapshot(it.key, DownloadState.CLOSED) })
        activeDownload.getAndSet(null)
            ?.completeExceptionally(IllegalStateException("регистрация загрузок закрыта"))
        remove(this)
    }

    private fun declaredSnapshots(): List<DownloadSnapshot> =
        request.files.map { DownloadSnapshot(it.key, DownloadState.DECLARED) }

    private fun List<FileDownload>.containsDependencies(): Boolean =
        any { it.key.startsWith(DEPENDENCY_PREFIX) }

    private companion object {
        const val DEPENDENCY_PREFIX = "dependency:"
    }
}
