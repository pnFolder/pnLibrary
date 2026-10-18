package ru.privatenull.pnlibrary.core.downloads

import ru.privatenull.pnlibrary.api.downloads.DownloadRegistration
import ru.privatenull.pnlibrary.api.downloads.DownloadSnapshot
import ru.privatenull.pnlibrary.api.downloads.DownloadState
import ru.privatenull.pnlibrary.api.downloads.FileDownload
import ru.privatenull.pnlibrary.api.downloads.FileDownloads
import ru.privatenull.pnlibrary.core.lifecycle.ResourceCleanup
import java.util.Collections
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Tracks the files of one download request and runs at most one download of them at a time. */
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
    private val stateLock = Any()
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
            promise.completeExceptionally(IllegalStateException("Download registration is closed"))
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

        synchronized(stateLock) {
            if (closed.get() || managerIsClosed()) {
                promise.completeExceptionally(IllegalStateException("Download registration is closed"))
                return promise
            }
            activeDownload.get()?.let { return it }
            activeDownload.set(promise)
            updateSnapshots(selectedFiles, DownloadState.DOWNLOADING)
        }
        try {
            executor.execute { execute(selectedFiles, promise) }
        } catch (error: Throwable) {
            synchronized(stateLock) {
                if (!closed.get() && !managerIsClosed()) {
                    currentSnapshots.set(request.files.map { DownloadSnapshot(it.key, DownloadState.FAILED, error.message) })
                }
                activeDownload.compareAndSet(promise, null)
            }
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
        val result = synchronized(stateLock) {
            if (closed.get() || managerIsClosed()) {
                promise.completeExceptionally(IllegalStateException("Download registration is closed"))
                return promise
            }
            currentSnapshots.set(request.files.map { DownloadSnapshot(it.key, DownloadState.BLOCKED, reason) })
            snapshots()
        }
        promise.complete(result)
        runCatching { blocked(reason) }
        return promise
    }

    private fun execute(
        declarations: List<FileDownload>,
        promise: CompletableFuture<List<DownloadSnapshot>>,
    ) {
        if (closed.get() || managerIsClosed()) {
            activeDownload.compareAndSet(promise, null)
            promise.complete(snapshots())
            return
        }
        val outcome = runCatching {
            install(declarations) {
                check(!closed.get() && !managerIsClosed()) { "Download registration is closed" }
            }
        }
        val result = try {
            synchronized(stateLock) {
                if (!closed.get() && !managerIsClosed()) {
                    val error = outcome.exceptionOrNull()
                    if (error == null) updateSnapshots(declarations, DownloadState.STAGED)
                    else currentSnapshots.set(request.files.map { DownloadSnapshot(it.key, DownloadState.FAILED, error.message) })
                }
                snapshots()
            }
        } finally {
            // Future callbacks may immediately start another download.
            activeDownload.compareAndSet(promise, null)
        }
        promise.complete(result)
        if (closed.get() || managerIsClosed()) return
        outcome.onSuccess {
            if (declarations.containsDependencies()) runCatching { dependenciesStaged(declarations) }
        }.onFailure { error -> ResourceCleanup.suppressInto(error, { failed(declarations, error) }) }
    }

    private fun updateSnapshots(activeFiles: List<FileDownload>, state: DownloadState) {
        currentSnapshots.set(request.files.map { file ->
            DownloadSnapshot(file.key, if (file in activeFiles) state else DownloadState.DECLARED)
        })
    }

    override fun close() {
        val pending = synchronized(stateLock) {
            if (!closed.compareAndSet(false, true)) return
            currentSnapshots.set(request.files.map { DownloadSnapshot(it.key, DownloadState.CLOSED) })
            activeDownload.getAndSet(null)
        }
        pending?.completeExceptionally(IllegalStateException("Download registration is closed"))
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
