package org.pnfolder.pnlibrary.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.config.ConfigAddress;
import org.pnfolder.pnlibrary.api.config.ConfigId;
import org.pnfolder.pnlibrary.api.config.ConfigInfo;
import org.pnfolder.pnlibrary.api.config.ConfigScope;
import org.pnfolder.pnlibrary.api.config.ConfigSerializerInfo;
import org.pnfolder.pnlibrary.api.config.ConfigTypeInfo;
import org.pnfolder.pnlibrary.api.config.ManagedConfig;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.spi.platform.PlatformLogging;
import org.pnfolder.pnlibrary.spi.platform.PlatformOwnership;
import org.pnfolder.pnlibrary.spi.platform.PlatformPnLogger;

/**
 * Hands out one configuration scope per module, rooted at the plugin's data folder, and closes them with their owner.
 *
 * <p>Every registered file is listed under its {@link ConfigId}, so files can be found by plugin, module or identifier.
 * A polymorphic type published with an address works in the files that address covers.
 */
public final class YamlConfigurations implements ConfigurationRuntime {
    private static final ModuleId MAIN_MODULE = ModuleId.of("main");

    private final PlatformOwnership platform;
    private final PlatformLogging logging;
    private final Map<Object, Map<PluginId, ModuleConfigScope>> scopes = new IdentityHashMap<Object, Map<PluginId, ModuleConfigScope>>();
    private final ConfigFileRegistry files = new ConfigFileRegistry();
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final ConfigAdditions additions = new ConfigAdditions(this::reloadWaiting);
    private volatile Function<ManagedConfigFile, AutoCloseable> fileListener;

    /** Creates the service with the default console logging. */
    public YamlConfigurations(@NotNull PlatformOwnership platform) {
        this(platform, new PlatformLogging() { });
    }

    /** Creates the service writing plugin logs through {@code logging}. */
    public YamlConfigurations(@NotNull PlatformOwnership platform, @NotNull PlatformLogging logging) {
        this.platform = platform;
        this.logging = logging;
    }

    /**
     * The listener told about every registered file, so that diagnostics can read it and hide its {@code @ConfigSecret}
     * fields. The returned handle is closed together with the scope.
     */
    @Nullable
    public Function<ManagedConfigFile, AutoCloseable> getFileListener() {
        return fileListener;
    }

    /** Sets the listener of {@link #getFileListener()}; it applies to files registered afterwards. */
    public void setFileListener(@Nullable Function<ManagedConfigFile, AutoCloseable> listener) {
        this.fileListener = listener;
    }

    /** Returns the scope of {@code owner}, using the plugin id the platform reports for it. */
    @Override
    @NotNull
    public ConfigScope scope(@NotNull Object owner) {
        return scope(owner, PluginId.of(ownerId(owner)));
    }

    /** Returns the scope of {@code owner} and {@code pluginId} as module {@code main}, creating it on first use. Fails after {@link #close()}. */
    @Override
    @NotNull
    public ConfigScope scope(@NotNull Object owner, @NotNull PluginId pluginId) {
        return scope(owner, pluginId, pluginId, MAIN_MODULE);
    }

    /**
     * Returns the scope of {@code owner} kept under {@code key} for module {@code module} of plugin {@code plugin}, creating it
     * on first use. Fails after {@link #close()}.
     */
    @Override
    @NotNull
    public ConfigScope scope(@NotNull Object owner, @NotNull PluginId key, @NotNull PluginId plugin, @NotNull ModuleId module) {
        synchronized (scopes) {
            if (closed.get()) {
                throw new IllegalStateException("Configuration service is closed");
            }
            Map<PluginId, ModuleConfigScope> byPlugin = scopes.get(owner);
            if (byPlugin == null) {
                byPlugin = new LinkedHashMap<PluginId, ModuleConfigScope>();
                scopes.put(owner, byPlugin);
            }
            ModuleConfigScope scope = byPlugin.get(key);
            if (scope == null) {
                scope = createScope(owner, key, plugin, module);
                byPlugin.put(key, scope);
            }
            return scope;
        }
    }

    private ModuleConfigScope createScope(Object owner, PluginId key, PluginId plugin, ModuleId module) {
        Path directory = ownerDirectory(owner).toAbsolutePath().normalize();
        return new ModuleConfigScope(key, plugin, module, directory, new PlatformPnLogger(logging, owner, key.getValue()),
                files, additions, () -> fileListener, scope -> forget(owner, key, scope));
    }

    private void forget(Object owner, PluginId key, ModuleConfigScope scope) {
        synchronized (scopes) {
            Map<PluginId, ModuleConfigScope> byPlugin = scopes.get(owner);
            if (byPlugin == null) {
                return;
            }
            if (byPlugin.get(key) == scope) {
                byPlugin.remove(key);
            }
            if (byPlugin.isEmpty()) {
                scopes.remove(owner);
            }
        }
    }

    /** Closes the scopes created for {@code owner} and forgets them. */
    @Override
    public void close(@NotNull Object owner) {
        List<ModuleConfigScope> owned = new ArrayList<ModuleConfigScope>();
        synchronized (scopes) {
            Map<PluginId, ModuleConfigScope> removed = scopes.remove(owner);
            if (removed != null) {
                owned.addAll(removed.values());
            }
        }
        for (ModuleConfigScope scope : owned) {
            scope.close();
        }
    }

    /** Closes every scope and rejects further {@code scope} calls. Repeated calls do nothing. */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        List<ModuleConfigScope> owned = new ArrayList<ModuleConfigScope>();
        synchronized (scopes) {
            for (Map<PluginId, ModuleConfigScope> byPlugin : scopes.values()) {
                owned.addAll(byPlugin.values());
            }
            scopes.clear();
        }
        for (ModuleConfigScope scope : owned) {
            scope.close();
        }
    }

    @Override
    @NotNull
    public List<ConfigInfo> all() {
        List<ConfigInfo> result = new ArrayList<ConfigInfo>();
        for (OwnedConfig<?> file : files.all()) {
            result.add(file.getInfo());
        }
        return result;
    }

    @Override
    @NotNull
    public List<ConfigInfo> find(@NotNull ConfigAddress address) {
        List<ConfigInfo> result = new ArrayList<ConfigInfo>();
        for (OwnedConfig<?> file : files.all()) {
            if (address.matches(file.getId())) {
                result.add(file.getInfo());
            }
        }
        return result;
    }

    @Override
    @Nullable
    public ManagedConfig<?> get(@NotNull ConfigId id) {
        return files.get(id);
    }

    @Override
    @NotNull
    public List<ConfigTypeInfo> types(@NotNull Class<?> baseType) {
        return additions.types(baseType);
    }

    @Override
    @NotNull
    public List<ConfigSerializerInfo> serializers() {
        return additions.serializers();
    }

    private String ownerId(Object owner) {
        String id = platform.ownerDetails(owner).get("id");
        return id != null ? id : owner.getClass().getSimpleName();
    }

    // Platforms without per-plugin folders (Velocity) get the conventional plugins/<id> directory.
    private Path ownerDirectory(Object owner) {
        Path folder = platform.ownerDataFolder(owner);
        return folder != null ? folder : Paths.get("plugins", ownerId(owner));
    }

    /** A new word may unblock files that failed or skipped elements for want of it: read those again. */
    private void reloadWaiting(ConfigAddress where) {
        // CodeFirstYaml has already logged why a read failed; a file that still fails keeps waiting.
        for (OwnedConfig<?> handle : files.waitingUnder(where)) {
            Failures.runCatchingNonFatal(handle::reload);
        }
    }
}
