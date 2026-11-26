package org.pnfolder.pnlibrary.bungee;

import java.util.Objects;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.api.plugin.PluginDescription;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.remote.context.RemotePolicyContext;
import org.pnfolder.pnlibrary.bungee.commands.BungeeCommandAdapter;
import org.pnfolder.pnlibrary.bungee.diagnostics.BungeeDiagnosticDetailsCollector;
import org.pnfolder.pnlibrary.bungee.metrics.BungeeMetricsFactory;
import org.pnfolder.pnlibrary.bungee.network.BungeeAudienceAdapter;
import org.pnfolder.pnlibrary.bungee.network.BungeeNetworkProxy;
import org.pnfolder.pnlibrary.bungee.remote.BungeeRemotePolicyContextFactory;
import org.pnfolder.pnlibrary.bungee.tasks.BungeeTaskAdapter;
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandAdapter;
import org.pnfolder.pnlibrary.spi.metrics.PlatformMetricsFactory;
import org.pnfolder.pnlibrary.spi.platform.JulLogObserver;
import org.pnfolder.pnlibrary.spi.platform.PlatformLogSink;
import org.pnfolder.pnlibrary.spi.platform.PlatformPlugins;
import org.pnfolder.pnlibrary.spi.platform.PlatformSnapshot;
import org.pnfolder.pnlibrary.spi.platform.PluginSnapshot;
import org.pnfolder.pnlibrary.spi.platform.ProxyPlatformAdapter;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;

/** Runtime adapter for BungeeCord: composes the native command, task, audience, metadata and log bridges. */
final class BungeePlatformAdapter extends ProxyPlatformAdapter {
    private final Plugin plugin;
    private final BungeeDiagnosticDetailsCollector diagnosticDetailsCollector;
    private final PlatformPlugins<Plugin> plugins;
    private final JulLogObserver logObserver;
    private final Path dataPath;
    private final PlatformMetricsFactory nativeMetricsFactory;
    private final BungeeAudienceAdapter nativeAudienceAdapter;
    private final PlatformCommandAdapter nativeCommandAdapter;
    private final PlatformTaskAdapter nativeTaskAdapter;
    private final PlatformLogSink nativeLogHandler;
    private BungeeNetworkProxy nativeNetworkProxy;

    BungeePlatformAdapter(@NotNull Plugin plugin, @NotNull Supplier<PnLogger> pnLogger) {
        super(plugin);
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.diagnosticDetailsCollector = new BungeeDiagnosticDetailsCollector(plugin);
        this.plugins = new PlatformPlugins<>("BungeeCord",
                () -> plugin.getProxy().getPluginManager().getPlugins(),
                owner -> owner instanceof Plugin ? (Plugin) owner : null,
                BungeePlatformAdapter::describe);
        this.logObserver = new JulLogObserver(() -> plugin.getProxy().getLogger(), plugin,
                loggerName -> JulLogObserver.matchPlugin(loggerName,
                        plugin.getProxy().getPluginManager().getPlugins(), p -> p.getDescription().getName()));
        this.dataPath = plugin.getDataFolder().toPath();
        this.nativeMetricsFactory = new BungeeMetricsFactory(pnLogger);
        this.nativeAudienceAdapter = new BungeeAudienceAdapter(plugin);
        this.nativeCommandAdapter = new BungeeCommandAdapter(nativeAudienceAdapter);
        this.nativeTaskAdapter = new BungeeTaskAdapter(plugin);
        this.nativeLogHandler = logObserver.logHandler(this::loggerFor);
    }

    private static PluginSnapshot describe(Plugin installed) {
        PluginDescription description = installed.getDescription();
        String author = description.getAuthor();
        List<String> authors = author == null
                ? Collections.emptyList() : Collections.singletonList(author);
        return new PluginSnapshot(description.getName(), description.getName(), description.getVersion(), authors,
                description.getMain());
    }

    private Logger loggerFor(Object owner) {
        Logger logger = owner instanceof Plugin ? ((Plugin) owner).getLogger() : null;
        return logger != null ? logger : plugin.getLogger();
    }

    @NotNull
    @Override
    public PlatformType getType() {
        return PlatformType.BUNGEECORD;
    }

    @NotNull
    @Override
    public Path getDataFolder() {
        return dataPath;
    }

    @NotNull
    @Override
    public String getImplementationName() {
        String name = plugin.getProxy().getName();
        return name.trim().isEmpty() ? getType().getDisplayName() : name;
    }

    @NotNull
    @Override
    public PlatformMetricsFactory getMetricsFactory() {
        return nativeMetricsFactory;
    }

    // Created on first use: the proxy is not read while the adapter is only constructed or closed.
    @NotNull
    @Override
    public synchronized BungeeNetworkProxy getNetworkProxy() {
        if (nativeNetworkProxy == null) {
            nativeNetworkProxy = new BungeeNetworkProxy(plugin.getProxy());
        }
        return nativeNetworkProxy;
    }

    @NotNull
    @Override
    public BungeeAudienceAdapter getPlayerAdapter() {
        return nativeAudienceAdapter;
    }

    @NotNull
    @Override
    public PlatformCommandAdapter getCommandAdapter() {
        return nativeCommandAdapter;
    }

    @NotNull
    @Override
    public PlatformTaskAdapter getTaskAdapter() {
        return nativeTaskAdapter;
    }

    @NotNull
    @Override
    public PlatformLogSink getLogHandler() {
        return nativeLogHandler;
    }

    @Override
    protected void observeProxyLogs(@Nullable PlatformLogSink observer) {
        logObserver.observe(observer);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void console(@NotNull Object owner, @NotNull String message) {
        plugin.getProxy().getConsole().sendMessage(TextComponent.fromLegacyText(message));
    }

    @Nullable
    @Override
    public PluginSnapshot ownerMetadata(@NotNull Object owner) {
        return plugins.metadata(owner, this);
    }

    @Nullable
    @Override
    public Path ownerDataFolder(@NotNull Object owner) {
        Plugin found = plugins.find(owner);
        return found != null ? found.getDataFolder().toPath() : null;
    }

    @Nullable
    @Override
    public Logger ownerLogger(@NotNull Object owner) {
        Plugin found = plugins.find(owner);
        return found != null ? found.getLogger() : null;
    }

    @NotNull
    @Override
    public Map<String, String> installedPlugins() {
        return plugins.versions();
    }

    @NotNull
    @Override
    public PlatformSnapshot snapshot() {
        ProxyServer proxy = plugin.getProxy();
        return new PlatformSnapshot(proxy.getName(), proxy.getVersion(), null, proxy.getOnlineCount(),
                new ArrayList<>(proxy.getServers().keySet()), plugins.snapshots());
    }

    @NotNull
    @Override
    public Map<String, Object> diagnosticDetails(boolean includeSensitive) {
        return diagnosticDetailsCollector.collect(snapshot(), includeSensitive);
    }

    @NotNull
    @Override
    public RemotePolicyContext remotePolicyContext(@NotNull Object owner, @NotNull Map<String, String> values) {
        return BungeeRemotePolicyContextFactory.create(plugins.requireInstalled(owner, "Remote policy owner"), values);
    }

    // BungeeCord has no main-thread scheduler; global work runs asynchronously.
    @Override
    public void executeGlobal(@NotNull Runnable task) {
        if (lifecycle.isClosed()) {
            return;
        }
        plugin.getProxy().getScheduler().runAsync(plugin, task);
    }
}
