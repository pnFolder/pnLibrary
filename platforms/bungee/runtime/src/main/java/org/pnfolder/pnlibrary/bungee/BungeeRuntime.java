package org.pnfolder.pnlibrary.bungee;

import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.bungee.api.BungeePlatform;
import org.pnfolder.pnlibrary.bungee.link.BungeeLinkListener;
import org.pnfolder.pnlibrary.bungee.network.BungeePlayerEvents;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.exceptions.ResourceCleanup;
import org.pnfolder.pnlibrary.core.link.LinkReceiver;
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntime;
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntimeHost;
import org.pnfolder.pnlibrary.core.runtime.ProxyRuntime;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Starts and stops the shared runtime on BungeeCord. It is public with one public constructor because
 * {@code PnLibraryBungeePlugin} creates it by reflection after the runtime libraries are loaded.
 */
public final class BungeeRuntime {
    private final Plugin plugin;
    private ProxyRuntime proxyRuntime;
    private PnLibraryRuntimeHost runtimeHost;
    private BungeePlayerEvents playerEvents;

    /** Creates the runtime of the pnLibrary plugin {@code plugin}. */
    public BungeeRuntime(@NotNull Plugin plugin) {
        this.plugin = plugin;
    }

    /** Starts the runtime; a failure stops what already started and is rethrown. */
    public void start() {
        try {
            open();
        } catch (Throwable error) {
            Failures.runCatchingNonFatal(() -> {
                stop();
                return null;
            }).onFailure(error::addSuppressed);
            throw error;
        }
    }

    private void open() {
        AtomicReference<PnLogger> metricsLogger = new AtomicReference<>();
        BungeePlatformAdapter adapter = new BungeePlatformAdapter(plugin,
                () -> Objects.requireNonNull(metricsLogger.get()));
        PnLibraryRuntimeHost host = PnLibraryRuntime.builder()
                .owner(plugin)
                .adapter(adapter)
                .platform(BungeePlatform.class, (BungeePlatform) plugin::getProxy)
                .launch(adapter::markReady);
        runtimeHost = host;
        metricsLogger.set(host.getLibrary().getLogging().logger(plugin, "Metrics"));
        ProxyRuntime proxy = new ProxyRuntime(adapter, this::registerLinkListener,
                adapter.getNetworkProxy()::describePlugin);
        proxyRuntime = proxy;
        proxy.start(host);
        playerEvents = new BungeePlayerEvents(host.getLibrary());
        plugin.getProxy().getPluginManager().registerListener(plugin, playerEvents);
    }

    private AutoCloseable registerLinkListener(LinkReceiver receiver) {
        BungeeLinkListener listener = new BungeeLinkListener(receiver);
        ProxyServer proxy = plugin.getProxy();
        proxy.getPluginManager().registerListener(plugin, listener);
        return () -> proxy.getPluginManager().unregisterListener(listener);
    }

    /** Closes everything that started; the first failure is thrown after all steps ran. */
    @SuppressWarnings("unchecked")
    public void stop() {
        ProxyRuntime proxy = proxyRuntime;
        BungeePlayerEvents events = playerEvents;
        PnLibraryRuntimeHost host = runtimeHost;
        proxyRuntime = null;
        playerEvents = null;
        runtimeHost = null;
        ResourceCleanup.closeAll(
                () -> {
                    if (proxy != null) {
                        proxy.close();
                    }
                },
                () -> {
                    if (events != null) {
                        plugin.getProxy().getPluginManager().unregisterListener(events);
                    }
                },
                () -> {
                    if (host != null) {
                        host.close();
                    }
                });
    }
}
