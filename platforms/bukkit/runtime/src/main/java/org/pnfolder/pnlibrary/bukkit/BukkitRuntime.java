package org.pnfolder.pnlibrary.bukkit;

import java.util.concurrent.atomic.AtomicReference;

import org.bukkit.plugin.java.JavaPlugin;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.bukkit.api.BukkitPlatform;
import org.pnfolder.pnlibrary.bukkit.audience.BukkitAdventureTransport;
import org.pnfolder.pnlibrary.bukkit.integration.BukkitIntegrations;
import org.pnfolder.pnlibrary.bukkit.inventory.BukkitMenus;
import org.pnfolder.pnlibrary.bukkit.inventory.MenuService;
import org.pnfolder.pnlibrary.bukkit.server.ServerInfo;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.core.lifecycle.RuntimeBridge;
import org.pnfolder.pnlibrary.core.link.Link;
import org.pnfolder.pnlibrary.core.runtime.PnLibraryRuntimeHost;

/**
 * Owns everything the Bukkit plugin starts: the shared runtime, Bukkit services and optional integrations.
 *
 * <p>{@link #start()} is transactional: a failure stops whatever was already created before the exception is
 * rethrown to Bukkit. {@link #stop()} releases every resource and is safe to call more than once.
 */
public final class BukkitRuntime {
    private final JavaPlugin plugin;
    private PnLibraryRuntimeHost host;
    private BukkitAdventureTransport audiences;
    private BukkitMenus menus;
    private BukkitIntegrations integrations;
    private BukkitLinkReporter linkReporter;
    private PnLogger logger;

    public BukkitRuntime(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        AtomicReference<PnLogger> startupLogger = new AtomicReference<>();
        AtomicReference<PnLogger> metricsLogger = new AtomicReference<>();
        PnLoggerBuffer logBuffer = new PnLoggerBuffer(plugin.getLogger(), startupLogger::get);
        BukkitAdventureTransport playerService = new BukkitAdventureTransport(plugin, logBuffer);
        audiences = playerService;
        Link network = BukkitLink.open(plugin);
        BukkitPlatformAdapter adapter = new BukkitPlatformAdapter(plugin, playerService, network, metricsLogger::get, logBuffer);
        try {
            PnLibraryRuntimeHost runtime = PnLibraryRuntimeHost.start(plugin, adapter);
            host = runtime;
            startupLogger.set(runtime.getLibrary().getLogging().logger(plugin, "Runtime"));
            logBuffer.flush();
            metricsLogger.set(runtime.getLibrary().getLogging().logger(plugin, "Metrics"));
            logger = startupLogger.get();
            installServices(runtime, adapter, playerService);
            startOptionalParts(runtime, adapter, network);
        } catch (Throwable error) {
            stop();
            throw error;
        }
    }

    public void stop() {
        release("link", linkReporter == null ? null : linkReporter::stop);
        linkReporter = null;
        release("integrations", integrations == null ? null : integrations::stop);
        integrations = null;
        release("menus", menus == null ? null : menus::close);
        menus = null;
        release("audiences", audiences == null ? null : audiences::close);
        audiences = null;
        release("library", host == null ? null : host::close);
        host = null;
        logger = null;
    }

    private void startOptionalParts(PnLibraryRuntimeHost runtime, BukkitPlatformAdapter adapter, Link network) {
        BukkitIntegrations optional = new BukkitIntegrations(plugin);
        integrations = optional;
        plugin.getServer().getPluginManager().registerEvents(optional, plugin);
        optional.start(runtime);
        linkReporter = new BukkitLinkReporter(plugin, runtime.getLibrary(), network, adapter.getScheduler());
        linkReporter.start();
    }

    /** One part failing to stop must not keep the others from stopping, but it must not vanish either. */
    private void release(String part, Runnable action) {
        if (action == null) {
            return;
        }
        Failures.runCatchingNonFatal(() -> {
            action.run();
            return null;
        }).onFailure(error -> {
            if (logger != null) {
                logger.error("Could not stop " + part, error);
            }
        });
    }

    private void installServices(PnLibraryRuntimeHost runtime, BukkitPlatformAdapter adapter,
                                 BukkitAdventureTransport playerService) {
        BukkitMenus menuService = new BukkitMenus(plugin, RuntimeBridge.runtimeTaskScope(runtime.getLibrary()),
            () -> runtime.getLibrary().getLogging().logger(plugin, "Menus"));
        ServerInfo serverInfo = adapter.getServerInfo();
        runtime.registerService(ServerInfo.class, serverInfo, 0);
        runtime.registerService(MenuService.class, menuService, 0);
        runtime.registerPlatform(BukkitPlatform.class, new BukkitPlatformServices(plugin.getServer(), serverInfo, menuService));
        runtime.registerService(BukkitAdventureTransport.class, playerService, 0);
        menus = menuService;
    }
}
