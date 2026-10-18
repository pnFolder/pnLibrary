package ru.privatenull.pnlibrary.core.downloads

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.downloads.DownloadDestination
import ru.privatenull.pnlibrary.api.downloads.DownloadState
import ru.privatenull.pnlibrary.api.downloads.FileDownload
import ru.privatenull.pnlibrary.api.downloads.FileDownloads
import java.util.concurrent.CompletionException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.TimeUnit

class DownloadRegistrationImplTest {
    @Test
    fun `future continuation can immediately start a fresh download`() {
        val queue = mutableListOf<Runnable>()
        val registration = registration(executor = Executor { queue += it })
        val first = registration.downloadNow().toCompletableFuture()
        val next = first.thenApply { registration.downloadNow().toCompletableFuture() }
        queue[0].run()
        val second = next.get(1, TimeUnit.SECONDS)
        assertNotSame(first, second)
        assertEquals(2, queue.size)
        queue[1].run()
        assertEquals(DownloadState.STAGED, second.get(1, TimeUnit.SECONDS).single().state)
        registration.close()
    }

    @Test
    fun `close between initial validation and job reservation cannot reopen registration`() {
        val queue = mutableListOf<Runnable>()
        var checks = 0
        lateinit var registration: DownloadRegistrationImpl
        registration = registration(
            executor = Executor { queue += it },
            managerIsClosed = {
                if (checks++ == 0) registration.close()
                false
            },
        )
        val result = registration.downloadNow().toCompletableFuture()
        assertTrue(result.isCompletedExceptionally)
        assertTrue(queue.isEmpty())
        assertEquals(DownloadState.CLOSED, registration.snapshots().single().state)
    }

    @Test
    fun `executor rejection records failure and releases the job reservation`() {
        val registration = registration(executor = Executor {
            throw RejectedExecutionException("executor stopped")
        })
        val first = registration.downloadNow().toCompletableFuture()
        assertThrows(CompletionException::class.java) { first.join() }
        assertEquals(DownloadState.FAILED, registration.snapshots().single().state)
        assertEquals("executor stopped", registration.snapshots().single().message)
        val second = registration.downloadNow().toCompletableFuture()
        assertNotSame(first, second)
        assertTrue(second.isCompletedExceptionally)
        registration.close()
        assertEquals(DownloadState.CLOSED, registration.snapshots().single().state)
    }

    @Test
    fun `notification failure cannot strand a failed download`() {
        val queue = mutableListOf<Runnable>()
        val original = IllegalStateException("download failed")
        val notification = IllegalArgumentException("notification failed")
        val registration = registration(
            executor = Executor { queue += it },
            install = { _, _ -> throw original },
            failed = { _, _ -> throw notification },
        )
        val first = registration.downloadNow().toCompletableFuture()
        assertDoesNotThrow { queue[0].run() }
        assertEquals(DownloadState.FAILED, first.get(1, TimeUnit.SECONDS).single().state)
        assertEquals(listOf(notification), original.suppressed.toList())
        val second = registration.downloadNow().toCompletableFuture()
        assertNotSame(first, second)
        assertDoesNotThrow { queue[1].run() }
        assertTrue(second.isDone)
        registration.close()
    }

    @Test
    fun `staging notification failure does not block later downloads`() {
        val queue = mutableListOf<Runnable>()
        val registration = registration(
            key = "dependency:example",
            executor = Executor { queue += it },
            dependenciesStaged = { throw IllegalStateException("notification failed") },
        )
        val first = registration.downloadNow().toCompletableFuture()
        assertSame(first, registration.downloadNow().toCompletableFuture())
        assertEquals(1, queue.size)
        assertDoesNotThrow { queue[0].run() }
        assertEquals(DownloadState.STAGED, first.get(1, TimeUnit.SECONDS).single().state)
        val second = registration.downloadNow().toCompletableFuture()
        assertNotSame(first, second)
        assertDoesNotThrow { queue[1].run() }
        assertEquals(DownloadState.STAGED, second.get(1, TimeUnit.SECONDS).single().state)
        registration.close()
    }

    private fun registration(
        key: String = "manual",
        executor: Executor,
        install: (List<FileDownload>, () -> Unit) -> Unit = { _, _ -> },
        failed: (List<FileDownload>, Throwable) -> Unit = { _, _ -> },
        dependenciesStaged: (List<FileDownload>) -> Unit = {},
        managerIsClosed: () -> Boolean = { false },
    ): DownloadRegistrationImpl = DownloadRegistrationImpl(
        request = FileDownloads.builder().file(key) {
            it.url("https://example.org/manual.bin").destination(DownloadDestination.DATA_FOLDER, "manual.bin")
        }.build(),
        configuration = DownloadConfiguration(),
        executor = executor,
        managerIsClosed = managerIsClosed,
        install = install,
        blocked = {},
        dependenciesStaged = dependenciesStaged,
        failed = failed,
        remove = {},
    )

    @Test
    fun `moves through declared staged and closed states`() {
        val request = FileDownloads.builder().file("manual") {
            it.url("https://example.org/manual.bin")
                .destination(DownloadDestination.DATA_FOLDER, "manual.bin")
        }.build()
        val installed = mutableListOf<String>()
        var removed = false
        val executor = Executors.newSingleThreadExecutor()
        try {
            val registration = DownloadRegistrationImpl(
                request = request,
                configuration = DownloadConfiguration(),
                executor = executor,
                managerIsClosed = { false },
                install = { declarations, _ -> installed += declarations.map { it.key } },
                blocked = {},
                dependenciesStaged = {},
                failed = { _, error -> throw error },
                remove = { removed = true },
            )

            assertEquals(DownloadState.DECLARED, registration.snapshots().single().state)
            assertEquals(
                DownloadState.STAGED,
                registration.downloadNow().toCompletableFuture().join().single().state,
            )
            assertEquals(listOf("manual"), installed)

            registration.close()
            assertTrue(registration.isClosed)
            assertTrue(removed)
            assertEquals(DownloadState.CLOSED, registration.snapshots().single().state)
        } finally {
            executor.shutdownNow()
        }
    }
}
