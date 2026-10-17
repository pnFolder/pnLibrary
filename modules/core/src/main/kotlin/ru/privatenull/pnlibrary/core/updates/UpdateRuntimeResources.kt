package ru.privatenull.pnlibrary.core.updates

import ru.privatenull.pnlibrary.core.updates.engine.ReleaseCatalogClient
import ru.privatenull.pnlibrary.core.updates.engine.ReleaseCatalogueStore
import ru.privatenull.pnlibrary.core.updates.engine.TrustedHttpClient
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Owns shared network and release-catalog resources used by the update pipeline. */
internal class UpdateRuntimeResources(dataFolder: Path) : AutoCloseable {
    private val catalogExecutor = Executors.newFixedThreadPool(4) { action ->
        Thread(action, "pnLibrary-catalog-fetch").apply { isDaemon = true }
    }
    private val catalogRequestExecutor = Executors.newFixedThreadPool(4) { action ->
        Thread(action, "pnLibrary-catalog-request").apply { isDaemon = true }
    }

    val http = TrustedHttpClient(Duration.ofSeconds(2), Duration.ofSeconds(3))
    private val releaseCatalog = ReleaseCatalogClient(
        http,
        ReleaseCatalogueStore(dataFolder.resolve("updates/catalog")),
        catalogExecutor,
        Duration.ofMinutes(30),
    )
    val catalogGateway = ReleaseCatalogGateway(
        client = releaseCatalog,
        http = http,
        requestExecutor = catalogRequestExecutor,
    )

    override fun close() {
        shutdown(catalogExecutor)
        shutdown(catalogRequestExecutor)
    }

    private fun shutdown(executor: ExecutorService) {
        executor.shutdownNow()
    }
}
