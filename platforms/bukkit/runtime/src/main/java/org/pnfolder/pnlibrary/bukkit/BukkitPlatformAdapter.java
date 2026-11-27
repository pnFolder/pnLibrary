package org.pnfolder.pnlibrary.bukkit;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.currency.CurrencyApi;
import org.pnfolder.pnlibrary.api.logging.LogLevel;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.remote.context.RemotePolicyContext;
import org.pnfolder.pnlibrary.api.network.channel.ServerNetwork;
import org.pnfolder.pnlibrary.api.runtime.PnLibrary;
import org.pnfolder.pnlibrary.bukkit.audience.BukkitAdventureTransport;
import org.pnfolder.pnlibrary.bukkit.audience.BukkitAudienceAdapter;
import org.pnfolder.pnlibrary.bukkit.commands.BukkitCommandAdapter;
import org.pnfolder.pnlibrary.bukkit.commands.BukkitControlCommand;
import org.pnfolder.pnlibrary.bukkit.compat.ServerCapabilities;
import org.pnfolder.pnlibrary.bukkit.integration.BukkitMetricsFactory;
import org.pnfolder.pnlibrary.bukkit.remote.BukkitRemotePolicyContextFactory;
import org.pnfolder.pnlibrary.bukkit.server.ServerInfo;
import org.pnfolder.pnlibrary.bukkit.tasks.BukkitTaskAdapter;
import org.pnfolder.pnlibrary.bukkit.tasks.ServerScheduler;
import org.pnfolder.pnlibrary.common.lang.Attempt;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.lifecycle.RuntimeBridge;
import org.pnfolder.pnlibrary.core.link.Link;
import org.pnfolder.pnlibrary.spi.audience.PlatformAudienceAdapter;
import org.pnfolder.pnlibrary.spi.commands.PlatformCommandAdapter;
import org.pnfolder.pnlibrary.spi.metrics.PlatformMetricsFactory;
import org.pnfolder.pnlibrary.spi.platform.JulLogObserver;
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter;
import org.pnfolder.pnlibrary.spi.platform.PlatformLifecycle;
import org.pnfolder.pnlibrary.spi.platform.PlatformLogSink;
import org.pnfolder.pnlibrary.spi.platform.PlatformPlugins;
import org.pnfolder.pnlibrary.spi.platform.PlatformSnapshot;
import org.pnfolder.pnlibrary.spi.platform.PluginSnapshot;
import org.pnfolder.pnlibrary.spi.platform.UncaughtExceptionLogObserver;
import org.pnfolder.pnlibrary.spi.tasks.PlatformTaskAdapter;

/**
 * Runtime adapter for Bukkit-compatible Minecraft servers.
 *
 * <p>The adapter supports legacy Bukkit, Paper, and Folia scheduler models. It owns the native command transport,
 * lifecycle listener, diagnostic collector, and native-log observer.
 */
final class BukkitPlatformAdapter implements PlatformAdapter {
    private final Plugin plugin;
    private final Link link;
    private final Supplier<PnLogger> pnLogger;
    private final PlatformLifecycle lifecycle = new PlatformLifecycle();
    private final PlatformPlugins<Plugin> plugins = BukkitPlugins.create();
    private final BukkitDiagnosticDetailsCollector diagnosticsCollector = new BukkitDiagnosticDetailsCollector();
    private final JulLogObserver logObserver;
    private final UncaughtExceptionLogObserver uncaughtExceptionObserver;
    private final PlatformMetricsFactory metricsFactory;
    private final PlatformCommandAdapter commandAdapter;
    private final PlatformAudienceAdapter audienceAdapter;
    private final PlatformTaskAdapter taskAdapter;
    private final PlatformLogSink logHandler;
    private final ServerScheduler scheduler;
    private final BukkitDispatcher dispatcher;
    private final Path dataPath;
    private final BukkitServerNetwork serverNetwork;
    private volatile ServerInfo serverInfo;
    private BukkitLifecycleListener lifecycleListener;

    BukkitPlatformAdapter(Plugin plugin, BukkitAdventureTransport playerService, Link link, Supplier<PnLogger> pnLogger,
                          PnLoggerBuffer logBuffer) {
        this.plugin = plugin;
        this.link = link;
        this.pnLogger = pnLogger;
        this.scheduler = new ServerScheduler(plugin);
        this.dispatcher = new BukkitDispatcher(scheduler, lifecycle::isClosed,
            error -> log(plugin, LogLevel.ERROR, "Не удалось передать задачу планировщику сервера", error));
        this.dataPath = plugin.getDataFolder().toPath();
        this.serverNetwork = new BukkitServerNetwork(plugin, pnLogger);
        this.logObserver = new JulLogObserver(Bukkit::getLogger, plugin,
            loggerName -> JulLogObserver.matchPlugin(loggerName, Arrays.asList(Bukkit.getPluginManager().getPlugins()),
                Plugin::getName));
        this.uncaughtExceptionObserver = new UncaughtExceptionLogObserver(plugin);
        this.metricsFactory = new BukkitMetricsFactory(pnLogger);
        this.commandAdapter = new BukkitCommandAdapter(playerService, plugin, pnLogger, logBuffer);
        this.audienceAdapter = new BukkitAudienceAdapter(playerService);
        this.taskAdapter = new BukkitTaskAdapter(plugin);
        // Writes to the owning plugin's logger, falling back to pnLibrary's own.
        this.logHandler = logObserver.logHandler(owner -> owner instanceof Plugin ? ((Plugin) owner).getLogger() : plugin.getLogger());
    }

    @Override
    public ServerNetwork getServerNetwork() {
        return serverNetwork;
    }

    /** Where pnLibrary's own work runs: the main thread, or on Folia the global region or a player's region. */
    ServerScheduler getScheduler() {
        return scheduler;
    }

    ServerInfo getServerInfo() {
        ServerInfo info = serverInfo;
        if (info == null) {
            info = new ServerInfo(getImplementationName(), Bukkit.getVersion(), ServerCapabilities.getMinecraftVersion(),
                ServerCapabilities.getRawMinecraftVersion());
            serverInfo = info;
        }
        return info;
    }

    @NotNull
    @Override
    public PlatformType getType() {
        return PlatformType.BUKKIT;
    }

    @Nullable
    @Override
    public String getMinecraftVersion() {
        return Failures.runCatchingNonFatal(ServerCapabilities::getRawMinecraftVersion).getOrNull();
    }

    @NotNull
    @Override
    public Path getDataFolder() {
        return dataPath;
    }

    @NotNull
    @Override
    public String getImplementationName() {
        String name = Bukkit.getName();
        return name.trim().isEmpty() ? getType().getDisplayName() : name;
    }

    @NotNull
    @Override
    public PlatformMetricsFactory getMetricsFactory() {
        return metricsFactory;
    }

    @NotNull
    @Override
    public PlatformCommandAdapter getCommandAdapter() {
        return commandAdapter;
    }

    @NotNull
    @Override
    public PlatformAudienceAdapter getPlayerAdapter() {
        return audienceAdapter;
    }

    @NotNull
    @Override
    public PlatformTaskAdapter getTaskAdapter() {
        return taskAdapter;
    }

    @NotNull
    @Override
    public PlatformLogSink getLogHandler() {
        return logHandler;
    }

    @Override
    public void console(@NotNull Object owner, @NotNull String message) {
        Bukkit.getConsoleSender().sendMessage(message);
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
        return found == null ? null : found.getDataFolder().toPath();
    }

    @Nullable
    @Override
    public Logger ownerLogger(@NotNull Object owner) {
        Plugin found = plugins.find(owner);
        return found == null ? null : found.getLogger();
    }

    @NotNull
    @Override
    public Map<String, String> installedPlugins() {
        return plugins.versions();
    }

    @NotNull
    @Override
    public PlatformSnapshot snapshot() {
        return new PlatformSnapshot(getImplementationName(), Bukkit.getBukkitVersion(), null,
            Bukkit.getOnlinePlayers().size(), Collections.emptyList(), plugins.snapshots());
    }

    @NotNull
    @Override
    public RemotePolicyContext remotePolicyContext(@NotNull Object owner, @NotNull Map<String, String> values) {
        return BukkitRemotePolicyContextFactory.create(plugins.requireInstalled(owner, "Remote policy owner"), values);
    }

    @Override
    public boolean disableOwner(@NotNull Object owner) {
        Plugin installed = plugins.requireInstalled(owner, "Disabled owner");
        BukkitDependentGuard.disableDependentsOf(installed, message -> pnLogger.get().warning(message));
        Bukkit.getPluginManager().disablePlugin(installed);
        return true;
    }

    /** Disables dependents as they enable and switches {@code owner} off once the server has started. */
    @Override
    public boolean disableDependents(@NotNull Object owner) {
        if (owner != plugin) {
            return false;
        }
        Attempt<Void> armed = Failures.runCatchingNonFatal(() -> {
            new BukkitDependentGuard(plugin, scheduler::nextTick, message -> pnLogger.get().warning(message)).arm();
            return null;
        });
        if (armed.isFailure()) {
            pnLogger.get().error("Could not watch plugins that depend on pnLibrary", armed.exceptionOrNull());
        }
        return armed.isSuccess();
    }

    /** Registers or removes the observer of server and plugin warnings, errors and uncaught thread exceptions. */
    @Override
    public synchronized void observeNativeLogs(@Nullable PlatformLogSink observer) {
        if (observer != null) {
            lifecycle.checkOpen();
        }
        logObserver.observe(observer);
        uncaughtExceptionObserver.observe(observer);
    }

    /** Registers commands and lifecycle listeners, rolling back partial initialization. */
    @Override
    public void bind(@NotNull PnLibrary library) {
        lifecycle.bind(() -> {
            lifecycleListener = lifecycle.track(new BukkitLifecycleListener(plugin, library, link).start());
            BukkitControlCommand control = lifecycle.track(
                new BukkitControlCommand(plugin, library, CurrencyApi.providers(library), link, scheduler));
            control.watchRestarts();
            return lifecycle.track(RuntimeBridge.registerRuntimeCommand(library, control.definition()));
        });
    }

    @NotNull
    @Override
    public Map<String, Object> diagnosticDetails(boolean includeSensitive) {
        return diagnosticsCollector.collect(this::snapshot, includeSensitive, this::executeGlobal);
    }

    @Override
    public void executeGlobal(@NotNull Runnable task) {
        dispatcher.global(task);
    }

    /** Runs work after server readiness, falling back to global dispatch. */
    @Override
    public void whenServerReady(@NotNull Runnable task) {
        BukkitLifecycleListener listener = lifecycleListener;
        if (listener != null) {
            listener.whenServerReady(task);
        } else {
            executeGlobal(task);
        }
    }

    /** Routes player replies to the player's thread, everything else to global dispatch. */
    @Override
    public void executeReply(@NotNull Object recipient, @NotNull Runnable task) {
        dispatcher.reply(recipient, task);
    }

    /** Releases registrations, cancels native tasks and stops native log observation once. */
    @Override
    public void close() {
        serverNetwork.close();
        lifecycle.closeAdapter(null, taskAdapter, () -> observeNativeLogs(null));
        lifecycleListener = null;
    }
}
