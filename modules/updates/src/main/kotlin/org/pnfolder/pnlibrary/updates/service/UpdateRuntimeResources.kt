package org.pnfolder.pnlibrary.updates.service

import org.pnfolder.pnlibrary.updates.engine.catalog.ReleaseCatalogClient
import org.pnfolder.pnlibrary.updates.engine.catalog.ReleaseCatalogStore
import org.pnfolder.pnlibrary.updates.engine.net.TrustedHttpClient
import org.pnfolder.pnlibrary.updates.install.ReleaseCatalogGateway
import org.pnfolder.pnlibrary.updates.policy.UpdateConfiguration
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Executors

/** Owns shared network and release-catalog resources used by the update pipeline. */
internal class UpdateRuntimeResources(catalogFolder: Path, configuration: UpdateConfiguration = UpdateConfiguration()) : AutoCloseable {
    private val catalogExecutor = Executors.newFixedThreadPool(4) { action ->
        Thread(action, "pnLibrary-catalog-fetch").apply { isDaemon = true }
    }
    private val catalogRequestExecutor = Executors.newFixedThreadPool(4) { action ->
        Thread(action, "pnLibrary-catalog-request").apply { isDaemon = true }
    }

    /** Short timeouts: catalogs are small and a slow answer should fall back to the cache, not stall the check. */
    val http = TrustedHttpClient(Duration.ofSeconds(2), Duration.ofSeconds(3))

    /** Patient client for update files, which can be large and come from a slow host. */
    val downloadHttp = TrustedHttpClient(configuration.downloads.connectTimeout, configuration.downloads.readTimeout)
    private val releaseCatalog = ReleaseCatalogClient(
        http,
        ReleaseCatalogStore(catalogFolder),
        catalogExecutor,
        Duration.ofMinutes(30),
    )
    val catalogGateway = ReleaseCatalogGateway(
        client = releaseCatalog,
        http = http,
        requestExecutor = catalogRequestExecutor,
    )

    override fun close() {
        catalogExecutor.shutdownNow()
        catalogRequestExecutor.shutdownNow()
    }
}
