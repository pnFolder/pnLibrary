package org.pnfolder.pnlibrary.core.plugin;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.pnfolder.pnlibrary.api.diagnostics.DiagnosticsService;
import org.pnfolder.pnlibrary.api.logging.LoggingService;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.metrics.MetricsService;
import org.pnfolder.pnlibrary.api.platform.PlatformType;
import org.pnfolder.pnlibrary.api.plugin.PluginBuilder;
import org.pnfolder.pnlibrary.api.plugin.PluginRegistration;
import org.pnfolder.pnlibrary.api.updates.UpdateService;
import org.pnfolder.pnlibrary.config.YamlConfigurations;
import org.pnfolder.pnlibrary.core.commands.engine.CommandService;
import org.pnfolder.pnlibrary.core.downloads.DownloadService;
import org.pnfolder.pnlibrary.core.events.EventDispatcher;
import org.pnfolder.pnlibrary.core.expansions.ExpansionRegistry;
import org.pnfolder.pnlibrary.core.placeholders.PlaceholderEngine;
import org.pnfolder.pnlibrary.core.plugin.module.library.LibraryFetcher;
import org.pnfolder.pnlibrary.core.plugin.module.ModulePreferences;
import org.pnfolder.pnlibrary.core.remote.monitor.RemotePolicyMonitor;
import org.pnfolder.pnlibrary.core.remote.report.RemotePolicyNoticeRenderer;
import org.pnfolder.pnlibrary.core.services.ServiceRegistry;
import org.pnfolder.pnlibrary.core.tasks.TaskService;
import org.pnfolder.pnlibrary.currency.CurrencyFeature;
import org.pnfolder.pnlibrary.spi.platform.PlatformAdapter;
import org.pnfolder.pnlibrary.spi.platform.PlatformIdentity;

/** Builds a {@link LoadedPlugins} out of recording stand-ins for the tests of plugin and module registration. */
public final class PluginRegistryFixture {
    private PlatformAdapter platform = platform();
    private EventDispatcher events = new EventDispatcher((plugin, message, error) -> { });
    private TaskService tasks = new TaskServiceRecorder(new TaskScopeRecorder(new Object()));
    private LoggingService logging = loggingService();
    private MetricsService metrics = new MetricsRecorder();
    private CommandService commands;
    private String libraryVersion;
    private UpdateService updates = emptyProxy(UpdateService.class);
    private DownloadService downloadService;
    private ExpansionRegistry expansions = new ExpansionRegistry((expansion, error) -> { });
    private ExpansionRegistry libraryExpansions = new ExpansionRegistry((expansion, error) -> { });
    private LibraryFetcher libraryFetcher = (loader, owner, plugin, library) -> null;
    private String minecraftVersion;

    private PluginRegistryFixture() {
    }

    public static PluginRegistryFixture builder() {
        return new PluginRegistryFixture();
    }

    public static LoadedPlugins registry() {
        return builder().build();
    }

    public PluginRegistryFixture platform(PlatformAdapter value) {
        this.platform = value;
        return this;
    }

    public PluginRegistryFixture events(EventDispatcher value) {
        this.events = value;
        return this;
    }

    public PluginRegistryFixture tasks(TaskService value) {
        this.tasks = value;
        return this;
    }

    public PluginRegistryFixture logging(LoggingService value) {
        this.logging = value;
        return this;
    }

    public PluginRegistryFixture metrics(MetricsService value) {
        this.metrics = value;
        return this;
    }

    public PluginRegistryFixture commands(CommandService value) {
        this.commands = value;
        return this;
    }

    public PluginRegistryFixture libraryVersion(String value) {
        this.libraryVersion = value;
        return this;
    }

    public PluginRegistryFixture updates(UpdateService value) {
        this.updates = value;
        return this;
    }

    public PluginRegistryFixture downloadService(DownloadService value) {
        this.downloadService = value;
        return this;
    }

    public PluginRegistryFixture expansions(ExpansionRegistry value) {
        this.expansions = value;
        return this;
    }

    public PluginRegistryFixture libraryExpansions(ExpansionRegistry value) {
        this.libraryExpansions = value;
        return this;
    }

    public PluginRegistryFixture libraryFetcher(LibraryFetcher value) {
        this.libraryFetcher = value;
        return this;
    }

    public PluginRegistryFixture minecraftVersion(String value) {
        this.minecraftVersion = value;
        return this;
    }

    public LoadedPlugins build() {
        PlatformIdentity identity = minecraftVersion == null ? platform : withMinecraftVersion(platform, minecraftVersion);
        return new LoadedPlugins(platform, identity, new RemotePolicyMonitor(platform, platform, new RemotePolicyNoticeRenderer(platform)),
            events, tasks, new ServiceRegistry(), logging, metrics, emptyProxy(DiagnosticsService.class), updates,
            new SharedFeatures(new PlaceholderEngine(), new CurrencyFeature(), expansions, libraryExpansions),
            new YamlConfigurations(platform), commands, downloadService, libraryVersion, new ModulePreferences(), libraryFetcher, platform);
    }

    private static PlatformIdentity withMinecraftVersion(PlatformAdapter platform, String version) {
        return (PlatformIdentity) Proxy.newProxyInstance(PlatformIdentity.class.getClassLoader(),
            new Class<?>[] {PlatformIdentity.class}, (proxy, method, args) -> {
                if (method.getName().equals("getMinecraftVersion")) {
                    return version;
                }
                try {
                    return method.invoke(platform, args);
                } catch (InvocationTargetException failure) {
                    throw failure.getCause();
                }
            });
    }

    /** The module of {@code owner}, registering the plugin first when it is new. */
    public static org.pnfolder.pnlibrary.api.plugin.ModuleContext registerModule(LoadedPlugins registry, Object owner, String id,
                                                                               Consumer<PluginBuilder> configure) {
        PluginRegistration plugin = registry.get(owner);
        return (plugin != null ? plugin : registry.register(owner)).registerModule(id, configure);
    }

    public static PlatformAdapter platform() {
        Map<String, String> details = new HashMap<>();
        details.put("id", "example");
        details.put("name", "Example");
        details.put("version", "1.0.0");
        details.put("authors", "pnFolder");
        return proxy(PlatformAdapter.class, name -> {
            switch (name) {
                case "getType":
                    return PlatformType.BUKKIT;
                case "getImplementationName":
                    return "Paper";
                case "acceptsOwner":
                    return true;
                case "ownerDetails":
                    return details;
                default:
                    return null;
            }
        });
    }

    public static LoggingService loggingService() {
        PnLogger logger = emptyProxy(PnLogger.class);
        Object box = new NoopMessageBox();
        return proxy(LoggingService.class, name -> {
            switch (name) {
                case "logger":
                    return logger;
                case "box":
                case "shutdownBox":
                    return box;
                default:
                    return null;
            }
        });
    }

    public static <T> T emptyProxy(Class<T> type) {
        return proxy(type, name -> null);
    }

    /** A proxy whose method answers come from {@code handler}; a missing answer is false, zero or nothing. */
    public static <T> T proxy(Class<T> type, Function<String, Object> handler) {
        Object value = Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            Object answer = handler.apply(method.getName());
            if (answer != null) {
                return answer;
            }
            Class<?> returnType = method.getReturnType();
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == int.class) {
                return 0;
            }
            return returnType == long.class ? (Object) 0L : null;
        });
        return type.cast(value);
    }

}
