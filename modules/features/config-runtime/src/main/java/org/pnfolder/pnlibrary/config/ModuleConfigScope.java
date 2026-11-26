package org.pnfolder.pnlibrary.config;

import java.util.Objects;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.pnfolder.pnlibrary.api.config.ConfigAddress;
import org.pnfolder.pnlibrary.api.config.ConfigId;
import org.pnfolder.pnlibrary.api.config.ConfigInfo;
import org.pnfolder.pnlibrary.api.config.ConfigOptions;
import org.pnfolder.pnlibrary.api.config.ConfigProblem;
import org.pnfolder.pnlibrary.api.config.ConfigRoot;
import org.pnfolder.pnlibrary.api.config.ConfigScope;
import org.pnfolder.pnlibrary.api.config.ConfigSerializer;
import org.pnfolder.pnlibrary.api.config.ConfigSerializerRegistration;
import org.pnfolder.pnlibrary.api.config.ConfigTypeConflict;
import org.pnfolder.pnlibrary.api.config.ConfigTypeRegistration;
import org.pnfolder.pnlibrary.api.config.ConfigValueValidator;
import org.pnfolder.pnlibrary.api.config.ManagedConfig;
import org.pnfolder.pnlibrary.api.logging.PnLogger;
import org.pnfolder.pnlibrary.api.plugin.ModuleId;
import org.pnfolder.pnlibrary.api.plugin.PluginId;
import org.pnfolder.pnlibrary.common.lang.Failures;
import org.pnfolder.pnlibrary.config.yaml.AnnotatedYamlCodec;
import org.pnfolder.pnlibrary.config.yaml.CodeFirstYaml;
import org.pnfolder.pnlibrary.config.yaml.schema.ConfigObjectIntrospector;
import org.pnfolder.pnlibrary.config.yaml.ConfigSettingsEditor;

/** One module's view of the service: its files, serializers and published types, all released on {@link #close()}. */
final class ModuleConfigScope implements ConfigScope {
    private final PluginId key;
    private final PluginId plugin;
    private final ModuleId module;
    private final Path directory;
    private final PnLogger logger;
    private final ConfigFileRegistry files;
    private final ConfigAdditions additions;
    private final Supplier<Function<ManagedConfigFile, AutoCloseable>> fileListener;
    private final Consumer<ModuleConfigScope> detach;
    private final ConfigPaths paths;
    private final Set<OwnedConfig<?>> handles = new LinkedHashSet<>();
    private final List<AutoCloseable> announcements = new ArrayList<>();
    private final Map<Class<?>, ConfigSerializer<?>> serializers = BuiltInConfigSerializers.create();
    private final AtomicBoolean scopeClosed = new AtomicBoolean(false);

    /**
     * Creates the scope of one module.
     *
     * @param key the identity the scope is kept under and its additions are released by
     * @param directory the data folder of the plugin
     * @param fileListener told about every file of the plugin itself, for diagnostics
     * @param detach called once when the scope closes, to forget it
     */
    ModuleConfigScope(@NotNull PluginId key, @NotNull PluginId plugin, @NotNull ModuleId module, @NotNull Path directory,
                      @NotNull PnLogger logger, @NotNull ConfigFileRegistry files, @NotNull ConfigAdditions additions,
                      @NotNull Supplier<Function<ManagedConfigFile, AutoCloseable>> fileListener, @NotNull Consumer<ModuleConfigScope> detach) {
        this.key = Objects.requireNonNull(key, "key");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.module = Objects.requireNonNull(module, "module");
        this.directory = Objects.requireNonNull(directory, "directory");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.files = Objects.requireNonNull(files, "files");
        this.additions = Objects.requireNonNull(additions, "additions");
        this.fileListener = Objects.requireNonNull(fileListener, "fileListener");
        this.detach = Objects.requireNonNull(detach, "detach");
        this.paths = new ConfigPaths(directory, plugin);
    }

    @Override
    @NotNull
    public PluginId getPlugin() {
        return plugin;
    }

    @Override
    @NotNull
    public ModuleId getModule() {
        return module;
    }

    @Override
    public int getSize() {
        synchronized (handles) {
            return handles.size();
        }
    }

    @Override
    @NotNull
    public <T> ManagedConfig<T> yaml(@NotNull String path, @NotNull Class<T> type, @NotNull Supplier<T> defaults) {
        return yaml(path, type, defaults, ConfigOptions.DEFAULT);
    }

    @Override
    @NotNull
    public <T> ManagedConfig<T> yaml(@NotNull String path, @NotNull Class<T> type, @NotNull Supplier<T> defaults,
                                     @NotNull ConfigOptions options, @NotNull ConfigValueValidator<T> validator) {
        if (scopeClosed.get()) {
            throw new IllegalStateException("Configuration scope is closed");
        }
        Path relative = Paths.get(path).normalize();
        boolean external = options.getRoot() == ConfigRoot.EXTERNAL;
        Path target = external ? paths.external(path, relative) : paths.checked(path, relative, options.getRoot());
        ConfigOptions effective = paths.effective(options);
        ConfigId id = new ConfigId(plugin, module, options.getRoot(), relative.toString().replace('\\', '/'));
        T defaultValue = defaults.get();
        if (defaultValue == null) {
            throw new IllegalStateException("Configuration defaults cannot be null");
        }
        AnnotatedYamlCodec<T> codec = new AnnotatedYamlCodec<>(type, defaults, () -> serializersFor(id),
                () -> additions.visibleTypes(id), plugin.getValue(), effective, message -> logger.warning(message));
        ConfigValueValidator<T> combined = value -> {
            List<ConfigProblem> problems = new ArrayList<>(codec.validate(value));
            problems.addAll(validator.validate(value));
            return problems;
        };
        CodeFirstYaml<T> handle = new CodeFirstYaml<>(target.toFile(), defaultValue, codec, logger, combined, effective);
        ConfigSettingsEditor editor = new ConfigSettingsEditor(new ConfigObjectIntrospector(effective.getNaming()), type);
        OwnedConfig<T> owned = new OwnedConfig<>(id, target, type, effective.getReadOnly(), handle, editor, logger);
        register(owned);
        // Files of other plugins can hold secrets that the diagnostic report must not see, so only the plugin's own are announced.
        if (options.getRoot() == ConfigRoot.PLUGIN) {
            announce(new ManagedConfigFile(key, directory, id.getPath(), codec.getSecretPaths(), codec.getDatabases()));
        } else if (external) {
            // The plugin's own file at a place of its choice: it is still the plugin's secrets, so they are hidden in reports too.
            announce(new ManagedConfigFile(key, target.getParent(), target.getFileName().toString(),
                    codec.getSecretPaths(), codec.getDatabases()));
        }
        return owned;
    }

    /** Adds {@code owned} to the library-wide list and to this scope; a second writer of one file is refused. */
    private void register(OwnedConfig<?> owned) {
        files.add(owned);
        try {
            synchronized (handles) {
                if (scopeClosed.get()) {
                    throw new IllegalStateException("Configuration scope is closed");
                }
                handles.add(owned);
            }
        } catch (Throwable error) {
            files.remove(owned.getId());
            throw error;
        }
    }

    // Announcing feeds diagnostics only, so a failing listener is logged and the file still works.
    private void announce(ManagedConfigFile file) {
        Function<ManagedConfigFile, AutoCloseable> listener = fileListener.get();
        if (listener == null) {
            return;
        }
        AutoCloseable announced;
        try {
            announced = listener.apply(file);
        } catch (Exception error) {
            logger.warning("Диагностика не получила файл " + file.getPath() + ": " + error.getMessage(), error);
            return;
        }
        if (announced == null) {
            return;
        }
        boolean keep;
        synchronized (handles) {
            keep = !scopeClosed.get() && announcements.add(announced);
        }
        if (!keep) {
            closeQuietly(announced);
        }
    }

    private static void closeQuietly(AutoCloseable closeable) {
        try {
            closeable.close();
        } catch (RuntimeException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }

    @Override
    @NotNull
    public <T> ConfigScope serializer(@NotNull Class<T> type, @NotNull ConfigSerializer<T> serializer) {
        if (scopeClosed.get()) {
            throw new IllegalStateException("Configuration scope is closed");
        }
        synchronized (serializers) {
            serializers.put(type, serializer);
        }
        return this;
    }

    /** What file {@code id} reads with: the module's own serializers first, then the additions that cover it. */
    private Map<Class<?>, ConfigSerializer<?>> serializersFor(ConfigId id) {
        Map<Class<?>, ConfigSerializer<?>> merged;
        synchronized (serializers) {
            merged = new LinkedHashMap<>(serializers);
        }
        for (Map.Entry<Class<?>, ConfigSerializer<?>> added : additions.visibleSerializers(id).entrySet()) {
            if (!merged.containsKey(added.getKey())) {
                merged.put(added.getKey(), added.getValue());
            }
        }
        return merged;
    }

    @Override
    @NotNull
    public <T> ConfigSerializerRegistration publishSerializer(@NotNull Class<T> type, @NotNull ConfigSerializer<T> serializer,
                                                              int priority, @Nullable ConfigAddress target) {
        return additions.publishSerializer(key, plugin, module, type, serializer, priority, target, () -> !scopeClosed.get());
    }

    @Override
    @NotNull
    public <T> ConfigTypeRegistration publishType(@NotNull Class<T> baseType, @NotNull Class<? extends T> implementation,
                                                  @NotNull String name, @NotNull Set<String> aliases, int priority,
                                                  @Nullable ConfigAddress target, @NotNull ConfigTypeConflict conflict) {
        return additions.publishType(key, plugin, module, baseType, implementation, name, aliases, priority, target,
                conflict, () -> !scopeClosed.get());
    }

    @Override
    @NotNull
    public List<ConfigInfo> list() {
        List<ConfigInfo> result = new ArrayList<>();
        for (OwnedConfig<?> handle : snapshot()) {
            result.add(handle.getInfo());
        }
        return result;
    }

    @Override
    @Nullable
    public ManagedConfig<?> get(@NotNull String path) {
        ConfigRoot root = ConfigRoot.PLUGIN;
        for (ConfigRoot candidate : ConfigRoot.values()) {
            if (candidate != ConfigRoot.PLUGIN && path.startsWith(candidate.getPrefix())) {
                root = candidate;
                break;
            }
        }
        String relative = path.substring(path.startsWith(root.getPrefix()) ? root.getPrefix().length() : 0).replace('\\', '/');
        String normalized = Failures.runCatchingNonFatal(
                () -> Paths.get(relative).normalize().toString().replace('\\', '/')).getOrNull();
        return normalized == null ? null : files.get(new ConfigId(plugin, module, root, normalized));
    }

    @Override
    public void loadAll() {
        forEach(snapshot(), ManagedConfig::load);
    }

    @Override
    public void reloadAll() {
        forEach(snapshot(), ManagedConfig::reload);
    }

    @Override
    public void saveAll() {
        List<OwnedConfig<?>> loaded = new ArrayList<>();
        for (OwnedConfig<?> handle : snapshot()) {
            if (handle.isLoaded() && !handle.isReadOnly()) {
                loaded.add(handle);
            }
        }
        forEach(loaded, ManagedConfig::save);
    }

    @Override
    public void close() {
        if (!scopeClosed.compareAndSet(false, true)) {
            return;
        }
        List<OwnedConfig<?>> owned;
        synchronized (handles) {
            owned = new ArrayList<>(handles);
            handles.clear();
        }
        ConfigRunAll.runAll(
                () -> forEach(owned, ManagedConfig::close),
                () -> owned.forEach(files::remove),
                () -> closeAnnouncements(),
                () -> additions.release(key),
                () -> detach.accept(this));
    }

    private void closeAnnouncements() {
        List<AutoCloseable> pending;
        synchronized (handles) {
            pending = new ArrayList<>(announcements);
            announcements.clear();
        }
        List<Runnable> steps = new ArrayList<>();
        for (AutoCloseable announced : pending) {
            steps.add(() -> closeQuietly(announced));
        }
        ConfigRunAll.runAll(steps);
    }

    private static void forEach(List<? extends ManagedConfig<?>> handles, Consumer<ManagedConfig<?>> action) {
        List<Runnable> steps = new ArrayList<>();
        for (ManagedConfig<?> handle : handles) {
            steps.add(() -> action.accept(handle));
        }
        ConfigRunAll.runAll(steps);
    }

    private List<OwnedConfig<?>> snapshot() {
        synchronized (handles) {
            return new ArrayList<>(handles);
        }
    }
}
