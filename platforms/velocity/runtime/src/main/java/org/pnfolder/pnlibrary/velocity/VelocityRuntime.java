package org.pnfolder.pnlibrary.velocity;

import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.proxy.ProxyServer;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.exceptions.ResourceCleanup;
import org.pnfolder.pnlibrary.core.link.LinkReceiver;
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntime;
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntimeHost;
import org.pnfolder.pnlibrary.core.runtime.ProxyRuntime;
import org.pnfolder.pnlibrary.velocity.api.VelocityPlatform;
import org.pnfolder.pnlibrary.velocity.link.VelocityLinkListener;
import org.pnfolder.pnlibrary.velocity.metrics.VelocityMetricsFactory;
import org.pnfolder.pnlibrary.velocity.network.VelocityPlayerEvents;

import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Velocity runtime behind the generated {@code PnLibraryVelocityPlugin} descriptor class.
 *
 * <p>The descriptor carries Velocity's {@code @Plugin} metadata and forwards lifecycle events here by reflection,
 * so the class and its one constructor are public. {@code owner} is the registered Velocity plugin instance,
 * which pnLibrary uses for identity and diagnostics. The initialize event creates one runtime host; the shutdown
 * event closes it.
 */
public final class VelocityRuntime {
    private final Object owner;
    private final ProxyServer server;
    private final Path dataDirectory;
    private ProxyRuntime proxyRuntime;
    private PnLibraryRuntimeHost runtimeHost;
    private VelocityPlayerEvents playerEvents;

    /** Creates the runtime of the plugin instance {@code owner}. */
    public VelocityRuntime(@NotNull Object owner, @NotNull ProxyServer server, @NotNull Path dataDirectory) {
        this.owner = owner;
        this.server = server;
        this.dataDirectory = dataDirectory;
    }

    /** Starts the shared runtime when Velocity announces proxy initialization. */
    public void onInitialize(@NotNull ProxyInitializeEvent event) {
        if (runtimeHost != null) {
            throw new IllegalStateException("Velocity runtime is already initialized");
        }
        try {
            open();
        } catch (Throwable error) {
            Failures.runCatchingNonFatal(() -> {
                close();
                return null;
            }).onFailure(error::addSuppressed);
            throw error;
        }
    }

    private void open() {
        AtomicReference<PnLogger> metricsLogger = new AtomicReference<>();
        VelocityPlatformAdapter adapter = new VelocityPlatformAdapter(owner, server,
                new VelocityMetricsFactory(server, dataDirectory, () -> Objects.requireNonNull(metricsLogger.get())),
                dataDirectory);
        PnLibraryRuntimeHost host = PnLibraryRuntime.builder()
                .owner(owner)
                .adapter(adapter)
                .platform(VelocityPlatform.class, (VelocityPlatform) () -> server)
                .launch(adapter::markReady);
        runtimeHost = host;
        metricsLogger.set(host.getLibrary().getLogging().logger(owner, "Metrics"));
        ProxyRuntime proxy = new ProxyRuntime(adapter, this::registerLinkListener,
                adapter.getNetworkProxy()::describePlugin);
        proxyRuntime = proxy;
        proxy.start(host);
        playerEvents = new VelocityPlayerEvents(host.getLibrary());
        server.getEventManager().register(owner, playerEvents);
    }

    private AutoCloseable registerLinkListener(LinkReceiver receiver) {
        VelocityLinkListener listener = new VelocityLinkListener(receiver);
        server.getEventManager().register(owner, listener);
        return () -> server.getEventManager().unregisterListener(owner, listener);
    }

    /** Closes the runtime before the proxy completes shutdown. */
    public void onShutdown(@NotNull ProxyShutdownEvent event) {
        close();
    }

    @SuppressWarnings("unchecked")
    private void close() {
        VelocityPlayerEvents events = playerEvents;
        ProxyRuntime proxy = proxyRuntime;
        PnLibraryRuntimeHost host = runtimeHost;
        playerEvents = null;
        proxyRuntime = null;
        runtimeHost = null;
        ResourceCleanup.closeAll(
                () -> {
                    if (events != null) {
                        server.getEventManager().unregisterListener(owner, events);
                    }
                },
                () -> {
                    if (proxy != null) {
                        proxy.close();
                    }
                },
                () -> {
                    if (host != null) {
                        host.close();
                    }
                });
    }
}
