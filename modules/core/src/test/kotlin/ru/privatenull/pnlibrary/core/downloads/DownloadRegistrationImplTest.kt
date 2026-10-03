package ru.privatenull.pnlibrary.core.downloads

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.privatenull.pnlibrary.api.downloads.DownloadDestination
import ru.privatenull.pnlibrary.api.downloads.DownloadState
import ru.privatenull.pnlibrary.api.downloads.FileDownloads
import java.util.concurrent.Executors

class DownloadRegistrationImplTest {
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
