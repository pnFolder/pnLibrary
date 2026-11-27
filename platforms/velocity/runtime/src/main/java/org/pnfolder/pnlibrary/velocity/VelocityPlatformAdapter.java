package org.pnfolder.pnlibrary.velocity;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.util.ProxyVersion;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.remote.context.RemotePolicyContext;
import org.pnfolder.pnlibrary.core.menu.OwnerInfo;
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandAdapter;
import org.pnfolder.pnlibrary.spi.metrics.PlatformMetricsFactory;
import org.pnfolder.pnlibrary.spi.platform.PlatformLogSink;
import org.pnfolder.pnlibrary.spi.platform.PlatformPlugins;
import org.pnfolder.pnlibrary.spi.platform.PlatformSnapshot;
import org.pnfolder.pnlibrary.spi.platform.PluginSnapshot;
import org.pnfolder.pnlibrary.spi.platform.ProxyPlatformAdapter;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter;
import org.pnfolder.pnlibrary.velocity.commands.VelocityCommandAdapter;
import org.pnfolder.pnlibrary.velocity.diagnostics.VelocityDiagnosticDetailsCollector;
import org.pnfolder.pnlibrary.velocity.log.VelocityLogObserver;
import org.pnfolder.pnlibrary.velocity.network.VelocityAudienceAdapter;
import org.pnfolder.pnlibrary.velocity.network.VelocityNetworkProxy;
import org.pnfolder.pnlibrary.velocity.remote.VelocityRemotePolicyContextFactory;
import org.pnfolder.pnlibrary.velocity.tasks.VelocityTaskAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runtime adapter for Velocity proxy servers.
 *
 * <p>Composes native command, task, audience, plugin metadata and diagnostics bridges. Startup-dependent
 * callbacks wait for {@link #markReady()}, called by the plugin entry point after runtime creation and native API
 * registration. Velocity has no entity-region scheduler.
 */
final class VelocityPlatformAdapter extends ProxyPlatformAdapter {
    private final ProxyServer proxy;
    private final PlatformMetricsFactory metricsFactory;
    private final Path dataFolder;
    private final Logger logger;
    private final VelocityDiagnosticDetailsCollector diagnostics;
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacySection();
    private final VelocityPluginLookup lookup;
    private final PlatformPlugins<PluginContainer> plugins;
    private final VelocityLogObserver logObserver;
    private final VelocityNetworkProxy networkProxy;
    private final VelocityAudienceAdapter audienceAdapter;
    private final PlatformCommandAdapter commandAdapter;
    private final PlatformTaskAdapter taskAdapter;
    private final PlatformLogSink logSink = this::forwardToNativeLogger;

    /** Creates the adapter; {@code plugin} is the pnLibrary plugin on whose behalf all tasks run. */
    VelocityPlatformAdapter(@NotNull Object plugin, @NotNull ProxyServer proxy,
                            @NotNull PlatformMetricsFactory metricsFactory, @NotNull Path dataFolder,
                            @NotNull Supplier<PnLogger> pnLogger) {
        super(plugin);
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.metricsFactory = Objects.requireNonNull(metricsFactory, "metricsFactory");
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder");
        this.logger = LoggerFactory.getLogger(plugin.getClass());
        this.diagnostics = new VelocityDiagnosticDetailsCollector(proxy);
        this.lookup = new VelocityPluginLookup(proxy.getPluginManager());
        this.plugins = new PlatformPlugins<>("Velocity", lookup::all, lookup::containerOf, lookup::describe);
        this.logObserver = new VelocityLogObserver(plugin, lookup::instanceById, lookup::instances);
        this.networkProxy = new VelocityNetworkProxy(proxy, (message, error) -> pnLogger.get().warning(message, error));
        this.audienceAdapter = new VelocityAudienceAdapter(proxy);
        this.commandAdapter = new VelocityCommandAdapter(proxy, audienceAdapter);
        this.taskAdapter = new VelocityTaskAdapter(proxy, plugin, this::warn);
    }

    /** The owner shown in menus for the plugin whose main object is {@code instance}; null when it is none. */
    @Nullable
    OwnerInfo ownerInfo(@Nullable Object instance) {
        return instance == null ? null : OwnerInfo.of(lookup.describe(plugins.find(instance)));
    }

    private void forwardToNativeLogger(Object owner, LogLevel level, String message, Throwable error) {
        switch (level) {
            case WARNING:
                logger.warn(message, error);
                break;
            case ERROR:
            case CRITICAL:
                logger.error(message, error);
                break;
            default:
                logger.info(message, error);
        }
    }

    @NotNull
    @Override
    public PlatformType getType() {
        return PlatformType.VELOCITY;
    }

    @NotNull
    @Override
    public Path getDataFolder() {
        return dataFolder;
    }

    @NotNull
    @Override
    public String getImplementationName() {
        String name = proxy.getVersion().getName();
        return name.trim().isEmpty() ? getType().getDisplayName() : name;
    }

    @NotNull
    @Override
    public PlatformMetricsFactory getMetricsFactory() {
        return metricsFactory;
    }

    @NotNull
    @Override
    public VelocityNetworkProxy getNetworkProxy() {
        return networkProxy;
    }

    @NotNull
    @Override
    public VelocityAudienceAdapter getPlayerAdapter() {
        return audienceAdapter;
    }

    @NotNull
    @Override
    public PlatformCommandAdapter getCommandAdapter() {
        return commandAdapter;
    }

    @NotNull
    @Override
    public PlatformTaskAdapter getTaskAdapter() {
        return taskAdapter;
    }

    @NotNull
    @Override
    public PlatformLogSink getLogHandler() {
        return logSink;
    }

    @Override
    protected void observeProxyLogs(@Nullable PlatformLogSink observer) {
        logObserver.observe(observer);
    }

    @Override
    public void console(@NotNull Object owner, @NotNull String message) {
        proxy.getConsoleCommandSource().sendMessage(legacySerializer.deserialize(message));
    }

    @Nullable
    @Override
    public PluginSnapshot ownerMetadata(@NotNull Object owner) {
        return plugins.metadata(owner, this);
    }

    @NotNull
    @Override
    public Map<String, String> installedPlugins() {
        return plugins.versions();
    }

    @NotNull
    @Override
    public PlatformSnapshot snapshot() {
        ProxyVersion version = proxy.getVersion();
        List<String> servers = new ArrayList<>();
        for (RegisteredServer server : proxy.getAllServers()) {
            servers.add(server.getServerInfo().getName());
        }
        return new PlatformSnapshot(version.getName(), version.getVersion(), version.getVendor(),
                proxy.getPlayerCount(), servers, plugins.snapshots());
    }

    @NotNull
    @Override
    public Map<String, Object> diagnosticDetails(boolean includeSensitive) {
        return diagnostics.collect(snapshot(), includeSensitive);
    }

    @NotNull
    @Override
    public RemotePolicyContext remotePolicyContext(@NotNull Object owner, @NotNull Map<String, String> values) {
        PluginContainer installed = plugins.requireInstalled(owner, "Remote policy owner");
        return VelocityRemotePolicyContextFactory.create(owner, installed, proxy, values);
    }

    /** Dispatches work on Velocity's scheduler on behalf of the pnLibrary plugin. */
    @Override
    public void executeGlobal(@NotNull Runnable task) {
        if (lifecycle.isClosed()) {
            return;
        }
        getTaskAdapter().buildTask().task(task).schedule();
    }
}
