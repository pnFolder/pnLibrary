package org.pnfolder.pnlibrary.bungee;

import net.md_5.bungee.api.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.bungee.api.BungeePlatform;
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntime;
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntimeHost;
import org.pnfolder.pnlibrary.core.runtime.ProxyRuntime;
import org.pnfolder.pnlibrary.core.runtime.ProxyStartup;

/** The BungeeCord side of the start: the adapter, the native platform object and the listeners. */
final class BungeeStartup implements ProxyStartup {
    private final Plugin plugin;
    private BungeePlatformAdapter adapter;
    private PnLibraryRuntimeHost host;

    BungeeStartup(Plugin plugin) {
        this.plugin = plugin;
    }

    @NotNull
    @Override
    public PnLibraryRuntimeHost launch() {
        adapter = new BungeePlatformAdapter(plugin, this::metricsLogger);
        host = PnLibraryRuntime.builder()
                .owner(plugin)
                .adapter(adapter)
                .platform(BungeePlatform.class, nativePlatform())
                .launch(adapter::markReady);
        return host;
    }

    @NotNull
    @Override
    public ProxyRuntime createProxy() {
        return new ProxyRuntime(adapter, adapter::ownerInfo);
    }

    @NotNull
    @Override
    public AutoCloseable listen(@NotNull PnLibraryRuntimeHost host) {
        return BungeeListeners.register(plugin, host.getLibrary(), adapter.getNetworkProxy());
    }

    private BungeePlatform nativePlatform() {
        return plugin::getProxy;
    }

    // Read when the first metrics session opens, which is after the host exists: the adapter is built before it.
    private PnLogger metricsLogger() {
        return host.getLibrary().getLogging().logger(plugin, "Metrics");
    }
}
