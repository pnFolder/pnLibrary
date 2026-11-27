package org.pnfolder.pnlibrary.velocity;

import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.file.Path;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.core.link.LinkReceiver;
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntime;
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntimeHost;
import org.pnfolder.pnlibrary.core.runtime.ProxyRuntime;
import org.pnfolder.pnlibrary.core.runtime.ProxyStartup;
import org.pnfolder.pnlibrary.velocity.api.VelocityPlatform;
import org.pnfolder.pnlibrary.velocity.link.VelocityLinkListener;
import org.pnfolder.pnlibrary.velocity.metrics.VelocityMetricsFactory;

/** The Velocity side of the start: the adapter, the native platform object and the listeners. */
final class VelocityStartup implements ProxyStartup {
    private final Object owner;
    private final ProxyServer server;
    private final Path dataDirectory;
    private VelocityPlatformAdapter adapter;
    private PnLibraryRuntimeHost host;

    VelocityStartup(Object owner, ProxyServer server, Path dataDirectory) {
        this.owner = owner;
        this.server = server;
        this.dataDirectory = dataDirectory;
    }

    @NotNull
    @Override
    public PnLibraryRuntimeHost launch() {
        VelocityMetricsFactory metrics = new VelocityMetricsFactory(server, dataDirectory, this::metricsLogger);
        adapter = new VelocityPlatformAdapter(owner, server, metrics, dataDirectory, this::networkLogger);
        host = PnLibraryRuntime.builder()
                .owner(owner)
                .adapter(adapter)
                .platform(VelocityPlatform.class, () -> server)
                .launch(adapter::markReady);
        return host;
    }

    @NotNull
    @Override
    public ProxyRuntime createProxy() {
        return new ProxyRuntime(adapter, this::registerLinkListener, adapter::ownerInfo);
    }

    @NotNull
    @Override
    public AutoCloseable listen(@NotNull PnLibraryRuntimeHost host) {
        return VelocityListeners.register(owner, server, host.getLibrary(), adapter.getNetworkProxy());
    }

    /** Read when the first metrics session opens, which is after the host exists; the factory is built before it. */
    private PnLogger networkLogger() {
        return host.getLibrary().getLogging().logger(owner, "Network");
    }

    private PnLogger metricsLogger() {
        return host.getLibrary().getLogging().logger(owner, "Metrics");
    }

    private AutoCloseable registerLinkListener(LinkReceiver receiver) {
        VelocityLinkListener listener = new VelocityLinkListener(receiver);
        server.getEventManager().register(owner, listener);
        return () -> server.getEventManager().unregisterListener(owner, listener);
    }
}
