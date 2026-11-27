package org.pnfolder.pnlibrary.updates.service.orchestrator;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.updates.engine.catalog.ReleaseCatalogClient;
import org.pnfolder.pnlibrary.updates.engine.catalog.ReleaseCatalogStore;
import org.pnfolder.pnlibrary.updates.engine.net.TrustedHttpClient;
import org.pnfolder.pnlibrary.updates.install.ReleaseCatalogGateway;
import org.pnfolder.pnlibrary.updates.policy.UpdateConfiguration;

/** Owns shared network and release-catalog resources used by the update pipeline. */
public final class UpdateRuntimeResources implements AutoCloseable {
    private final ExecutorService catalogExecutor = daemonPool("pnLibrary-catalog-fetch");
    private final ExecutorService catalogRequestExecutor = daemonPool("pnLibrary-catalog-request");
    private final TrustedHttpClient http;
    private final TrustedHttpClient downloadHttp;
    private final ReleaseCatalogGateway catalogGateway;

    /** Creates the clients and executors; the catalog cache lives in {@code catalogFolder}. */
    public UpdateRuntimeResources(@NotNull Path catalogFolder, @NotNull UpdateConfiguration configuration) {
        this.http = new TrustedHttpClient(Duration.ofSeconds(2), Duration.ofSeconds(3));
        this.downloadHttp = new TrustedHttpClient(configuration.getDownloads().getConnectTimeout(),
                configuration.getDownloads().getReadTimeout());
        ReleaseCatalogClient releaseCatalog = new ReleaseCatalogClient(http, new ReleaseCatalogStore(catalogFolder),
                catalogExecutor, Duration.ofMinutes(30));
        this.catalogGateway = new ReleaseCatalogGateway(releaseCatalog, http, catalogRequestExecutor);
    }

    /** Short timeouts: catalogs are small and a slow answer should fall back to the cache, not stall the check. */
    @NotNull
    public TrustedHttpClient getHttp() {
        return http;
    }

    /** Patient client for update files, which can be large and come from a slow host. */
    @NotNull
    public TrustedHttpClient getDownloadHttp() {
        return downloadHttp;
    }

    /** Loads release catalogs. */
    @NotNull
    public ReleaseCatalogGateway getCatalogGateway() {
        return catalogGateway;
    }

    @Override
    public void close() {
        catalogExecutor.shutdownNow();
        catalogRequestExecutor.shutdownNow();
    }

    private static ExecutorService daemonPool(String name) {
        return Executors.newFixedThreadPool(4, action -> {
            Thread thread = new Thread(action, name);
            thread.setDaemon(true);
            return thread;
        });
    }
}
