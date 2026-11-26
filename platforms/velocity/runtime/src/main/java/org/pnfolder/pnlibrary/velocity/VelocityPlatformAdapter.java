package org.pnfolder.pnlibrary.velocity;

import java.util.Objects;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.util.ProxyVersion;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.remote.context.RemotePolicyContext;
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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Runtime adapter for Velocity proxy servers.
 *
 * <p>Composes native command, task, audience, plugin metadata and diagnostics bridges. Startup-dependent
 * callbacks wait for {@link #markReady()}, called by the plugin entry point after runtime creation and native API
 * registration. Velocity has no entity-region scheduler.
 */
final class VelocityPlatformAdapter extends ProxyPlatformAdapter {
    private final ProxyServer proxy;
    private final PlatformMetricsFactory nativeMetricsFactory;
    private final Path dataPath;
    private final Logger nativeLogger;
    private final VelocityDiagnosticDetailsCollector diagnosticDetailsCollector;
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacySection();
    private final PlatformPlugins<PluginContainer> plugins;
    private final VelocityLogObserver logObserver;
    private final VelocityNetworkProxy nativeNetworkProxy;
    private final VelocityAudienceAdapter nativeAudienceAdapter;
    private final PlatformCommandAdapter nativeCommandAdapter;
    private final PlatformTaskAdapter nativeTaskAdapter;
    private final PlatformLogSink nativeLogHandler = this::forwardToNativeLogger;

    /** Creates the adapter; {@code plugin} is the pnLibrary plugin on whose behalf all tasks run. */
    VelocityPlatformAdapter(
            @NotNull Object plugin,
            @NotNull ProxyServer proxy,
            @NotNull PlatformMetricsFactory metricsFactory,
            @NotNull Path dataFolder) {
        super(plugin);
        this.proxy = Objects.requireNonNull(proxy, "proxy");
        this.nativeMetricsFactory = Objects.requireNonNull(metricsFactory, "metricsFactory");
        this.dataPath = Objects.requireNonNull(dataFolder, "dataFolder");
        this.nativeLogger = LoggerFactory.getLogger(plugin.getClass());
        this.diagnosticDetailsCollector = new VelocityDiagnosticDetailsCollector(proxy);
        this.plugins = new PlatformPlugins<>("Velocity",
                () -> proxy.getPluginManager().getPlugins(),
                owner -> proxy.getPluginManager().fromInstance(owner).orElse(null),
            container -> container != null ? describe(container) : null);
        this.logObserver = new VelocityLogObserver(plugin,
                id -> proxy.getPluginManager().getPlugin(id.toLowerCase(Locale.ROOT))
                        .flatMap(PluginContainer::getInstance).orElse(null),
                () -> instances(proxy));
        this.nativeNetworkProxy = new VelocityNetworkProxy(proxy);
        this.nativeAudienceAdapter = new VelocityAudienceAdapter(proxy);
        this.nativeCommandAdapter = new VelocityCommandAdapter(proxy, nativeAudienceAdapter);
        this.nativeTaskAdapter = new VelocityTaskAdapter(proxy, plugin, this::warn);
    }

    private static PluginSnapshot describe(PluginContainer container) {
        PluginDescription description = container.getDescription();
        return new PluginSnapshot(description.getId(), description.getName().orElse(description.getId()),
                description.getVersion().orElse("unknown"), description.getAuthors());
    }

    private static Collection<Object> instances(ProxyServer proxy) {
        List<Object> instances = new ArrayList<>();
        for (PluginContainer container : proxy.getPluginManager().getPlugins()) {
            container.getInstance().ifPresent(instances::add);
        }
        return instances;
    }

    private void forwardToNativeLogger(Object owner, LogLevel level,
                                       String message, Throwable error) {
        switch (level) {
            case WARNING:
                nativeLogger.warn(message, error);
                break;
            case ERROR:
            case CRITICAL:
                nativeLogger.error(message, error);
                break;
            default:
                nativeLogger.info(message, error);
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
        return dataPath;
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
        return nativeMetricsFactory;
    }

    @NotNull
    @Override
    public VelocityNetworkProxy getNetworkProxy() {
        return nativeNetworkProxy;
    }

    @NotNull
    @Override
    public VelocityAudienceAdapter getPlayerAdapter() {
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
        return diagnosticDetailsCollector.collect(snapshot(), includeSensitive);
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
